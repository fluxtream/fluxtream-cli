parser grammar TParser;

options {

    // Default language but name it anyway
    //
    language  = Java;

    // Produce an AST
    //
//    output    = AST;

    // Use a superclass to implement all helper
    // methods, instance variables and overrides
    // of ANTLR default methods, such as error
    // handling.
    //
    superClass = AbstractTParser;

    // Use the vocabulary generated by the accompanying
    // lexer. Maven knows how to work out the relationship
    // between the lexer and parser and will build the 
    // lexer before the parser. It will also rebuild the
    // parser if the lexer changes.
    //
    tokenVocab = TLexer;
}

// Some imaginary tokens for tree rewrites
//
tokens {
    COMMAND;
}

// What package should the generated source exist in?
//
@header {
    package com.fluxtream.cli;
}

@members {
	
	Client client;
	
	public TParser(org.antlr.runtime.CommonTokenStream tokenStream, Client client) {
		super(tokenStream);
		this.client = client;
	}
}

// This is just a simple parser for demo purpose
//
a  : command EOF
   ;

command
   : login |
     log |
     get |
     exit
   ;

exit
  : EXIT
    { System.exit(0); }
  ;

log
   : LOG
   { System.out.println("log"); }
   ;

login

   : LOGIN host=HOST username=ID
   { client.prompt($host.getText(), $username.getText()); }
   ;
   
get
   : GET connector=ID OAUTH_TOKENS
   { client.get("/api/guest/" + $connector.getText() + "/oauthTokens"); }
   ;
