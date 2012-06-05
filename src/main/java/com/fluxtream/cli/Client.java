package com.fluxtream.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

import com.sun.org.apache.bcel.internal.generic.GETFIELD;
import com.sun.tools.javac.jvm.Pool;
import jline.console.ConsoleReader;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

public class Client {

    public enum Method {
        GET, DELETE, PUT, POST
    }

    static class Command {
        public String name, prettyName, uri;
        public Method method;
        List<String> parameters = new ArrayList<String>();
        public Command(String name, String prettyName, Method method, String uri) {
            this.name = name;
            this.prettyName = prettyName;
            this.method = method;
            this.uri = uri;
        }
        public boolean hasParameters() {return parameters.size()>0;}
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
                String methodString = commandDefinition.split(" ")[0];
                String uri = commandDefinition.split(" ")[1];
                String prettyName = props.getProperty(commandName + ".prettyName");
//                Method method = Method.valueOf(Method.class, methodString); // why it doesn't work is a mystery to me
                Method method = getMethod(methodString);
                Command command = new Command(commandName, prettyName, method, uri);
                commandsByPrettyName.put(prettyName, command);
                commandsByDefinition.put(commandDefinition, command);
            }
        }
    }

    private Method getMethod(String methodString) {
        if (methodString.equals("GET"))
            return Method.GET;
        else if (methodString.equals("DELETE"))
            return Method.DELETE;
        else if (methodString.equals("PUT"))
            return Method.PUT;
        else if (methodString.equals("POST"))
            return Method.POST;
        else
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

	public void parseLine(String line) {
        line = line.trim();
        if (line.startsWith("exit"))
            System.exit(0);
        else {
            if (commandsByDefinition.containsKey(line))
                executeCommand(commandsByDefinition.get(line));
            else if (commandsByPrettyName.containsKey(line))
                executeCommand(commandsByPrettyName.get(line));
            else
                System.out.println("I am sorry, " + username + ". I don't understand this command.");
        }
	}

    private void executeCommand(Command command) {
        switch(command.method) {
            case GET:
                get("api" + command.uri);
                break;
        }
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
            System.out.println("GETting " + url);
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
