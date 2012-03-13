package com.fluxtream.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;

import jline.console.ConsoleReader;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.apache.http.Header;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

public class Client {
	
    static TLexer lexer = new TLexer();
    ConsoleReader consoleReader;
   
    DefaultHttpClient httpClient;
    String username, password;
    String host;
    
	public static void main(String [] args) throws Exception {
		Client client = null;
		if (args.length==2) {
			String host = args[0];
			if (!host.endsWith("/"))
				host += "/";
			client = new Client(host, args[1]);
		}
		else
			client = new Client();
		client.run();
	}
	
	public Client() {}
	
	public Client(String host, String username) throws MalformedURLException {
		this.username = username;
		this.host = host;
	}
	
	public void run() throws IOException, RecognitionException {
		String line = null;
		this.consoleReader = new ConsoleReader(System.in, new PrintWriter(System.out));
		if (this.host!=null&&this.username!=null) {
			line = "login " + this.host + " " + this.username;
		}
		while(true) {
			if (line!=null) parseLine(lexer, line);
			line = consoleReader.readLine("flx> ");
		}
	}
	
	public void parseLine(TLexer lexer, String line) throws RecognitionException {
        lexer.setCharStream(new ANTLRStringStream(line));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TParser parser = new TParser(tokens, this);

        parser.a();

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

}
