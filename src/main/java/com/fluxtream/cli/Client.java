package com.fluxtream.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.org.apache.bcel.internal.generic.GETFIELD;
import com.sun.tools.javac.jvm.Pool;
import jline.console.ConsoleReader;

import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

public class Client {

    public enum Method {
        GET, DELETE, PUT, POST
    }

    static class Parameter {
        String name, description, value = "";

        Parameter(String name) {
            this.name = name;
        }

        Parameter(String name, String description) {
            this.name = name;
            this.description = description;
        }
    }

    static class Command {

        String name, prettyName, uri;
        Method method;
        Pattern pattern = Pattern.compile("\\{(.+?)\\}");
        List<String> parameterNames = new ArrayList<String>();
        Map<String,Parameter> parameters = new HashMap<String,Parameter>();

        Command(String name, String prettyName, Method method, String uri) {
            this.name = name;
            this.prettyName = prettyName;
            this.method = method;
            this.uri = parseUri(uri);
        }

        private String parseUri(String uri) {
            Matcher matcher = pattern.matcher(uri);
            while (matcher.find())
                addParameter(matcher.group(1));
            return uri;
        }

        boolean hasParameters() {return parameters.size()>0;}

        void addParameter(String parameterName) {
            // preserve parameter order
            parameterNames.add(parameterName);
            parameters.put(parameterName,  new Parameter(parameterName));
        }

        void addParameter(String parameterName, String parameterDesc) {
            parameterNames.add(parameterName);
            parameters.put(parameterName,  new Parameter(parameterName, parameterDesc));
        }

        void setParameterValue(String parameterName, String parameterValue) {
            parameters.get(parameterName).value = parameterValue;
        }

        Collection<Parameter> getParameters() {
            // preserve parameter order
            Collection<Parameter> params = new ArrayList<Parameter>();
            for (String parameterName : parameterNames)
                params.add(parameters.get(parameterName));
            return params;
        }

        public String uri() {
            StringBuilder builder = new StringBuilder();
            Matcher matcher = pattern.matcher(uri);
            int i = 0;
            while (matcher.find()) {
                String replacement = parameters.get(matcher.group(1)).value;
                builder.append(uri.substring(i, matcher.start()));
                builder.append(replacement);
                i = matcher.end();
            }
            builder.append(uri.substring(i, uri.length()));
            return builder.toString();
        }

        public void resetParameters() {
            for (String parameterName : parameterNames)
                parameters.get(parameterName).value = "";
        }
    }

    ConsoleReader consoleReader;
   
    DefaultHttpClient httpClient;
    String username, password;
    String host;
    Map<String, Command> commandsByPrettyName = new HashMap<String,Command>();
    Map<String, Command> commandsByDefinition = new HashMap<String,Command>();

    public static void main(String [] args) throws Exception {
		Client client = null;
		if (args.length==2) {
			String host = args[0];
			if (!host.endsWith("/"))
				host += "/";
            if (!host.startsWith("http"))
                host = "http://" + host;
			client = new Client(host, args[1]);
		}
		else
			client = new Client();
		client.run();
	}
	
	public Client() {}
	
	public Client(String host, String username) throws IOException {
		this.username = username;
		this.host = host;
        reload();
	}

    private void reload() throws IOException {
        commandsByPrettyName.clear();
        commandsByDefinition.clear();
        loadCommands("admin-commands");
        loadCommands("dashboard-commands");
    }

    private void loadCommands(String propertyFile) throws IOException {
        Properties props = new Properties();
        final InputStream inputStream = ClassLoader.getSystemResourceAsStream(propertyFile + ".properties");
        props.load(inputStream);
        for (Entry<Object, Object> entry : props.entrySet()) {
            final String key = (String) entry.getKey();
            if (key.endsWith(".command")){
                String commandName = key.split("\\.")[0];
                String commandDefinition = props.getProperty(key);
                Pattern pattern = Pattern.compile("POST|PUT|DELETE|GET");
                final Matcher matcher = pattern.matcher(commandDefinition);
                matcher.find(0);
                String methodString = matcher.group(0);
                String uri = commandDefinition.substring(methodString.length()+1);
                String prettyName = props.getProperty(commandName + ".prettyName");
                Method method = getMethod(methodString);
                Command command = new Command(commandName, prettyName, method, uri);
                addCommandParameters(command, props);
                commandsByPrettyName.put(prettyName, command);
                commandsByDefinition.put(commandDefinition, command);
            }
        }
    }

    private void addCommandParameters(Command command, Properties props) {
        final String parameters = props.getProperty(command.name + ".params");
        if (parameters==null)
            return;
        StringTokenizer st = new StringTokenizer(parameters,  ",");
        while (st.hasMoreTokens()) {
            String parameterString = st.nextToken().trim();
            String[] parts = parameterString.split("/");
            if (parts.length==1)
                command.addParameter(parts[0].trim());
            else
                command.addParameter(parts[0].trim(), parts[1].trim());
        }
    }

    private Method getMethod(String methodString) {
        final Method[] values = Method.values();
        for (Method value : values)
            if (methodString.equals(value.toString()))
                return value;
        return null;
    }

    public void run() throws IOException {
		String line = null;
		this.consoleReader = new ConsoleReader(System.in, new PrintWriter(System.out));
		if (this.host!=null&&this.username!=null)
            prompt(this.host, this.username);
		while(true) {
			if (line!=null) parseLine(line);
			line = consoleReader.readLine("flx> ");
		}
	}

	public void parseLine(String line) throws IOException {
        line = line.trim();
        if (line.startsWith("exit"))
            System.exit(0);
        else if (line.startsWith("help"))
            help();
        else if (line.startsWith("set proxy"))
            setProxy();
        else if (line.startsWith("reload"))
            reload();
        else {
            if (commandsByDefinition.containsKey(line))
                executeCommand(commandsByDefinition.get(line));
            else if (commandsByPrettyName.containsKey(line))
                executeCommand(commandsByPrettyName.get(line));
            else
                System.out.println("I am sorry, " + username + ". I don't understand this command.");
        }
	}

    private void setProxy() throws IOException {
        String ip = consoleReader.readLine("Proxy Host/IP: ");
        String port = consoleReader.readLine("Proxy Port: ");
        HttpHost proxy = new HttpHost(ip, Integer.valueOf(port), "http");
        httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY,proxy);
    }

    private void help() {
        System.out.println("set proxy: set http proxy (e.g. for debugging)");
        System.out.println("reload: reload command definitions");
        System.out.println("exit: quit");
        for (String s : commandsByPrettyName.keySet()) {
            Command command = commandsByPrettyName.get(s);
            System.out.println(command.prettyName + ": " + command.method + " (/api)" + command.uri);
        }
    }

    private void executeCommand(Command command) throws IOException {
        promptParameters(command);
        final Map<String, String> params = toParameterMap(command.getParameters());
        switch(command.method) {
            case GET:
                get("api" + command.uri());
                break;
            case POST:
                post("api" + command.uri(), params);
                break;
            case DELETE:
                delete("api" + command.uri());
                break;
            case PUT:
                put("api" + command.uri());
                break;
        }
    }

    private Map<String, String> toParameterMap(Collection<Parameter> parameters) {
        Map<String,String> params = new HashMap<String,String>();
        for (Parameter parameter : parameters)
            params.put(parameter.name, parameter.value);
        return params;
    }

    private void promptParameters(Command command) throws IOException {
        command.resetParameters();
        final Collection<Parameter> parameters = command.getParameters();
        consoleReader.setHistoryEnabled(false);
        for (Parameter parameter : parameters) {
            String value = "";
            if (parameter.description!=null)
                value = consoleReader.readLine(parameter.description + ": ");
            else
                value = consoleReader.readLine(parameter.name + ": ");
            command.setParameterValue(parameter.name, value);
        }
        consoleReader.setHistoryEnabled(true);
    }

    public void log() {
		System.out.println("log in Client");
	}
	
	public void prompt(String url, String username) {
		String password;
		try {
			consoleReader.setPrompt("password: ");
			password = consoleReader.readLine(new Character('*'));
			this.host = url;
			this.username = username;
			this.password = password;
			consoleReader.setPrompt("flx> ");
			getHttpClient();
			if (url.endsWith("/"))
				get(url.endsWith("/")?"api/guest/":"/api/guest/");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void getHttpClient() throws MalformedURLException {
		if (this.httpClient==null) {
        	httpClient = new DefaultHttpClient();
        	URL hostUrl = new URL(this.host);
            httpClient.getCredentialsProvider().setCredentials(
                    new AuthScope(hostUrl.getHost(), hostUrl.getPort()),
                    new UsernamePasswordCredentials(username, password));
		}
	}

    public void put(String url) {
        try {
            HttpPut httpPut = new HttpPut( host + url);
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
            BasicScheme scheme = new BasicScheme();

            Header authorizationHeader = scheme.authenticate(credentials, httpPut);
            httpPut.addHeader(authorizationHeader);

            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            String response = httpClient.execute(httpPut, responseHandler);
            System.out.println(response);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public void post(String url, Map<String,String> params) {
        try {
        	
            HttpPost httppost = new HttpPost( host + url);
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
			BasicScheme scheme = new BasicScheme();
			
			Header authorizationHeader = scheme.authenticate(credentials, httppost);
			httppost.addHeader(authorizationHeader);

			Iterator<Entry<String, String>> iterator = params.entrySet().iterator();
			
	        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(params.size());
			while (iterator.hasNext()) {
				Entry<String,String> entry = iterator.next();
		        nameValuePairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
			}
			httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
			
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            String response = httpClient.execute(httppost, responseHandler);
            System.out.println(response);
        } catch(Exception e) {
        	e.printStackTrace();
        }
	}
	
	public void get(String url) {
        try {
            HttpGet httpget = new HttpGet( host + url);
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
			BasicScheme scheme = new BasicScheme();
			
			Header authorizationHeader = scheme.authenticate(credentials, httpget);
			httpget.addHeader(authorizationHeader);
            
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            String response = httpClient.execute(httpget, responseHandler);
            System.out.println(response);
        } catch(Exception e) {
        	e.printStackTrace();
        }
	}
	
	public void delete(String url) {
        try {
            HttpDelete httpdelete = new HttpDelete ( host + url);
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password);
			BasicScheme scheme = new BasicScheme();
			
			Header authorizationHeader = scheme.authenticate(credentials, httpdelete);
			httpdelete.addHeader(authorizationHeader);
            
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            String response = httpClient.execute(httpdelete, responseHandler);
            System.out.println(response);
        } catch(Exception e) {
        	e.printStackTrace();
        }
	}

}
