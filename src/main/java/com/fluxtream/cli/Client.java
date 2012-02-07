package com.fluxtream.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;

import jline.console.ConsoleReader;

import org.antlr.runtime.ANTLRStringStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

public class Client {
	
    static TLexer lexer = new TLexer();
    ConsoleReader consoleReader;
   
    DefaultHttpClient httpClient;
    String username, password;
    URL host;
    
	public static void main(String [] args) throws Exception {
		Client client = new Client();
		client.run();
	}
	
	public void run() throws IOException, RecognitionException {
		String line;
		this.consoleReader = new ConsoleReader(System.in, new PrintWriter(System.out));
		while((line = consoleReader.readLine("flx> "))!=null) {
			parseLine(lexer, line);
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
			this.host = new URL(url);
			this.username = username;
			this.password = password;
			consoleReader.setPrompt("flx> ");
			getHttpClient();
			get("api/guest/");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void getHttpClient() {
		if (this.httpClient==null) {
	        HttpHost proxy = new HttpHost("127.0.0.1", 8899, "http");
        	httpClient = new DefaultHttpClient();
            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
            httpClient.getCredentialsProvider().setCredentials(
                    new AuthScope(this.host.getHost(), this.host.getPort()),
                    new UsernamePasswordCredentials(username, password));
		}
	}
	
	public void get(String url) throws IOException {
        try {
            HttpGet httpget = new HttpGet( this.host.toString() + url);
            UsernamePasswordCredentials credentials = new
			UsernamePasswordCredentials(username, password);
			BasicScheme scheme = new BasicScheme();
//			Header authorizationHeader = scheme.authenticate(credentials, httpRequest);
//			httpget.addHeader(authorizationHeader);
            
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            String response = httpClient.execute(httpget, responseHandler);
            System.out.println(response);
        } catch(Exception e) {
        	e.printStackTrace();
        }
	}
	
}
