package io.sentry.example;

import io.sentry.Sentry;
import io.sentry.event.Breadcrumb.Level;
import io.sentry.event.Breadcrumb.Type;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.Event;
import io.sentry.event.EventBuilder;
import io.sentry.event.UserBuilder;
import io.sentry.event.helper.ShouldSendEventCallback;

import org.apache.logging.log4j.ThreadContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Controller
@CrossOrigin
@EnableAutoConfiguration
@SpringBootApplication
public class Application {

    private static final Logger logger = LogManager.getLogger("example.Application");

    private static Map<String, Integer> inventory = new HashMap<>();

    private void checkout(List<Item> cart) {
        Map<String, Integer> tempInventory = inventory;

        for (Item item : cart) {
            int currentInventory = tempInventory.get(item.getId());
            if (currentInventory <= 0) {
            	//Fix RT...
               throw new RuntimeException("No inventory for " + item.getId());
            }

            tempInventory.put(item.getId(), currentInventory-1);
        }
        inventory = tempInventory;
    }

    @PostMapping(value="/checkout", consumes = "application/json")
    @ResponseBody
    public ResponseEntity checkoutCart(@RequestHeader(name = "X-Session-ID", required = true) String sessionId,
                                       @RequestHeader(name = "X-Transaction-ID", required = true) String transactionId,
                                       @RequestBody Order order) {
        try {
            // set session and transaction id as tags
        	ThreadContext.put("page", "checkout");
            ThreadContext.put("session_id", sessionId);
            ThreadContext.put("transaction_id", transactionId);

            String userEmail = order.getEmail();
            logger.info("Processing order for: " + userEmail);
            
            // Set the user in the current context.
            UserBuilder userBuilder = new UserBuilder();
            userBuilder.setEmail(userEmail);
            Sentry.getContext().setUser( userBuilder.build());
                        
            Sentry.getContext().recordBreadcrumb(
                new BreadcrumbBuilder().setMessage("Processing checkout for user: " + userEmail).setType(Type.USER).setLevel(Level.CRITICAL).setCategory("custom").build()
            );

            
            // perform checkout
            checkout(order.getCart());
            
        } catch (Exception e) {
        	
            // log error + return 500, if exception
            logger.error("Error while checking out", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Checkout error");
        }

        // return 200 if checkout was successful
        return ResponseEntity.status(HttpStatus.OK).body("Success");
    }

    @RequestMapping("/capture-message")
    @ResponseBody
    String captureMessage() {
    	
    	ThreadContext.put("page", "capture-message");
        
    	// MDC extras (added to Sentry event as ADDITIONAL DATA) 
        ThreadContext.put("extra_key", "extra_value");
   
        // NDC extras are sent under 'log4j2-NDC'
        ThreadContext.push("Extra_details");

        logger.debug("Debug message");
        logger.info("Info message");
        logger.warn("Warn message"); // warning message that will be sent to Sentry
        return "Success";
    }

    @RequestMapping("/handled")
    @ResponseBody
    String handledError() {
    	
    	ThreadContext.put("page", "handled");
    	
        String someLocalVariable = "stack locals";
        
        try {
            int example = 1 / 0;
        } catch (Exception e) {
            // caught exception that will be sent to Sentry
            logger.error("Caught exception!", e);
        }
        return "Success";
    }
    
    @RequestMapping("/filtered")
    @ResponseBody
    String handledFilteredError() {
    	ThreadContext.put("page", "filtered");
        try {
            int example = 1 / 0;
        } catch (Exception e) {
            logger.error("Message contains foo!", e);
        }
        return "Success";
    }  
    

    @RequestMapping("/unhandled")
    @ResponseBody
    String unhandledError() {
    	ThreadContext.put("page", "unhandled");
    	 String someLocalVariable = "stack locals";
    	 
        throw new RuntimeException("Unhandled exception!");
    }
    
    @RequestMapping("/captureevent")
    @ResponseBody
    String captureEvent() {
    	 // This sends an event to Sentry.
        EventBuilder eventBuilder = new EventBuilder()
                        .withMessage("This is a test")
                        .withLevel(Event.Level.INFO)
                        .withLogger(logger.getName());
        
        Map<String, Map<String, Object>> contexts = new  HashMap<String, Map<String, Object>>();
        Map<String, Object> contextA = new  HashMap<String, Object>();
        contextA.put("a1", "1");
        contextA.put("a2", "2");
        contextA.put("a3", "3");
        contexts.put("Context A", contextA);
        
        Map<String, Object> contextB = new  HashMap<String, Object>();
        contextB.put("b1", "1");
        contextB.put("b2", "2");
        contextB.put("b3", "3");
        contexts.put("Context B", contextB);
        
        eventBuilder.withContexts(contexts);

        // Note that the *unbuilt* EventBuilder instance is passed in so that
        // EventBuilderHelpers are run to add extra information to your event.
        Sentry.capture(eventBuilder);
        
        return "Event sent to Sentry";
    }

    public static void main(String[] args) {
    	//initSentry();
        inventory.put("wrench", 0);
        inventory.put("nails", 0);
        inventory.put("hammer", 2);
        SpringApplication.run(Application.class, args);
    }
    
    private static void initSentry() {
    	Sentry.init();
        
    	Sentry.getStoredClient().setServerName("fe1");
    	
    	//Added as tags to Sentry event
        Sentry.getStoredClient().addTag("dynamicTag1", "1.0");
        
        Sentry.getStoredClient().addShouldSendEventCallback(new ShouldSendEventCallback() {
		    @Override
		    public boolean shouldSend(Event event) {
		    	 
		        if (event.getMessage().contains("foo")) {
		            return false;
		        }		   
		
		        return true;
		    }
		});
    }
}
