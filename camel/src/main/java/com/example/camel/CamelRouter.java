/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.camel;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static org.apache.camel.model.rest.RestParamType.body;
import static org.apache.camel.model.rest.RestParamType.path;

/**
 * A simple Camel route that triggers from a timer and calls a bean and prints
 * to system out.
 * <p/>
 * Use <tt>@Component</tt> to make Camel auto-detect this route when starting.
 */
@Component
public class CamelRouter extends RouteBuilder {

	protected static final String SELECT_QUERY = "select * from products";
	protected static final String DELETE_QUERY = "DELETE FROM products WHERE id= ${header.id}";

	@Autowired
	private Environment env;

	@Value("${camel.servlet.mapping.context-path}")
	private String contextPath;

	@Override
	public void configure() throws Exception {

		// @formatter:off
	        
	        // this can also be configured in application.properties
	        restConfiguration()
	            .component("servlet")
	            .bindingMode(RestBindingMode.json)   //global configuration, incoming and outgoing request are json
	            .dataFormatProperty("prettyPrint", "true")
	            .enableCORS(true)
	            .port(env.getProperty("server.port", "8080"))
	            .contextPath(contextPath.substring(0, contextPath.length() - 2))   //change it to /api
	            // turn on openapi api-doc
	            .apiContextPath("/api-doc")
	            .apiProperty("api.title", "Products API")
	            .apiProperty("api.version", "1.0.0");

	        rest().description("Actor REST service")
	            .consumes("application/json")  //incoming request is json
	            .produces("application/json")   //outgoing request is also json

	            .get("/products")
                .to("direct:read")
                
                .post("/products")
                .to("direct:insert")
	        
		        .delete("/products/{id}")
		        .to("direct:delete")
	        
		        .put("/products/{id}")
		        .to("direct:update");
		        
	        
	        from("direct:insert")
	        .setHeader("sqlQuery", simple("insert into products(name, category, created_at, updated_at) values('${header.name}', '${header.category}', CURRENT_DATE, CURRENT_DATE)"))
	        .log("Received message to insert with header: ${header.name} & ${header.category}")
	        .to("direct:persist"); 
	        
	        
	        
	        from("direct:read")
            .setBody(simple(SELECT_QUERY))
            .to("spring-jdbc:default");

	        
	        from("direct:delete")
	        .setHeader("sqlQuery", simple("DELETE FROM products WHERE id= ${header.id}"))
	        .log("Received message to delete with header: ${header.id}" )
	        .process(exchange -> {
	            // Retrieve the 'id' parameter from the headers
	            String id = exchange.getIn().getHeader("id", String.class);
	            // Output the 'id' value
	            System.out.println("Deleting record with id: " + id);
	        })
	        .to("direct:persist"); 
	        
	        
	        from("direct:update")
	        .setHeader("sqlQuery", simple("update products set name = '${header.name}', category = '${header.category}', updated_at = CURRENT_DATE WHERE id = ${header.id}"))
            .log("Received message to update with header: ${header.name} & ${header.category}")
            .to("direct:persist");
	        
	        //commit the query if header is correct , else rollback
	        from("direct:persist")
	        .log("Executing SQL query: ${header.sqlQuery}")
            .choice().when(simple("${header.fail} == 'true'"))
                    .to("direct:rollback")
                .otherwise()
                    .to("direct:commit");

		    from("direct:commit")
		    .setBody(simple("${header.sqlQuery}")) 
		            .transacted()
		                .to("spring-jdbc:default?resetAutoCommit=false")
		            .setBody(constant("executed"));
		
		    from("direct:rollback")
		    .setBody(simple("${header.sqlQuery}")) 
		            .transacted()
		                .to("spring-jdbc:default?resetAutoCommit=false")
		            .rollback("forced to rollback");
	        
	       

	}
}