/*
 * @author-name: Rishab Katta.
 *
 *
 * PublisherAgent class is used to perform all the functions that a Publisher in a pub-sub model can perform.
 */
package edu.rit.cs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thetransactioncompany.jsonrpc2.client.*;
import com.thetransactioncompany.jsonrpc2.*;
import com.thetransactioncompany.jsonrpc2.server.Dispatcher;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;

import java.net.*;
import java.io.*;
import java.util.*;
import java.lang.reflect.Type;
import com.google.gson.reflect.TypeToken;

/*
 * PublisherAgentHandler is a service that listens to (on Port 6969) requests from the EventManager. It's a wrapper
 * class for all the Handler classes that handle different type of requests from the EventManager.
 */
class PublisherAgentHandler {

    // Implements a handler for "receiveTopic" JSON-RPC method
    public static class ReceiveTopicsHandler implements RequestHandler {


        // Reports the method names of the handled requests
        public String[] handledRequests() {

            return new String[]{"receiveTopic"};
        }


        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("receiveTopic")) {

                // get the topic sent by EM from myParams and print it out.
                Map<String, Object> myParams = req.getNamedParams();
                Gson gson = new Gson();
                Topic topic = gson.fromJson(myParams.get("topic").toString(), Topic.class);
                String topicName = topic.getName();
                System.out.println("New Topic Received: " + topicName);

                return new JSONRPC2Response(topicName, req.getID());

            } else { return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID()); }
        }
    }

    // Implements a handler for "receivePendingNotifications" JSON-RPC method
    public static class ReceivePendingNotificationsHandler implements RequestHandler {


        // Reports the method names of the handled requests
        public String[] handledRequests() {

            return new String[]{"receivePendingNotifications"};
        }


        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("receivePendingNotifications")) {

                // get pending notifications list from myParams sent by EventManager.
                Map<String, Object> myParams = req.getNamedParams();
                Gson gson = new Gson();
                Type listType = new TypeToken<ArrayList<Object>>(){}.getType();
                List<Object> pendingNotificationsList = gson.fromJson(myParams.get("pendingNotifications").toString(), listType);

                for (Object o: pendingNotificationsList ) {
                    String topicOrEvent = o.toString();
                    boolean isTopic = false;
                    JsonParser parser = new JsonParser();
                    JsonElement element = parser.parse(topicOrEvent);
                    JsonObject obj = element.getAsJsonObject();
                    Set<Map.Entry<String, JsonElement>> entries = obj.entrySet();
                    if (entries.size()==3){
                        isTopic = true;
                    }
                    if (isTopic){
                        Topic pendingTopic = gson.fromJson(o.toString(), Topic.class);
                        System.out.println("New topic Received : "+ pendingTopic.getName());
                    }
                }

                return new JSONRPC2Response("Pending notifications received.", req.getID());

            } else { return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());}
        }
    }
}

/*
 * PHandler is the thread that takes the requests from EM and allocates it a different port
 * and maintains a "registry" of all different types of handlers and assigns a handler to process in that thread.
 */
class PHandler extends Thread {
    private String name;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Dispatcher dispatcher;

    /**
     * Constructs a handler thread, squirreling away the socket.
     * All the interesting work is done in the run method.
     */
    public PHandler(Socket socket) {
        this.socket = socket;

        // Create a new JSON-RPC 2.0 request dispatcher
        this.dispatcher = new Dispatcher();

        // Register the "echo", "receiveTopic" and "receivePendingNotifications" handlers with it
        dispatcher.register(new PublisherAgentHandler.ReceiveTopicsHandler());
        dispatcher.register(new PublisherAgentHandler.ReceivePendingNotificationsHandler());


    }

    /**
     * Services this thread's client by repeatedly requesting a
     * screen name until a unique one has been submitted, then
     * acknowledges the name and registers the output stream for
     * the client in a global set, then repeatedly gets inputs and
     * broadcasts them.
     */
    public void run() {
        try {
            // Create character streams for the socket.
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            // read request
            String line;
            line = in.readLine();
            //System.out.println(line);
            StringBuilder raw = new StringBuilder();
            raw.append("" + line);
            boolean isPost = line.startsWith("POST");
            int contentLength = 0;
            while (!(line = in.readLine()).equals("")) {
                //System.out.println(line);
                raw.append('\n' + line);
                if (isPost) {
                    final String contentHeader = "Content-Length: ";
                    if (line.startsWith(contentHeader)) {
                        contentLength = Integer.parseInt(line.substring(contentHeader.length()));
                    }
                }
            }
            StringBuilder body = new StringBuilder();
            if (isPost) {
                int c = 0;
                for (int i = 0; i < contentLength; i++) {
                    c = in.read();
                    body.append((char) c);
                }
            }

            JSONRPC2Request request = JSONRPC2Request.parse(body.toString());
            JSONRPC2Response resp = dispatcher.process(request, null);


            // send response
            out.write("HTTP/1.1 200 OK\r\n");
            out.write("Content-Type: application/json\r\n");
            out.write("\r\n");
            out.write(resp.toJSONString());
            out.flush();
            out.close();
            socket.close();

        } catch (IOException e) {
            System.out.println(e);
        } catch (JSONRPC2ParseException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                System.out.println("Exception occured when trying to close socket connection.");
            }
        }
    }
}

/*
 * PublisherAgent is the sort of Main class which calls other classes. It makes requests to the EventManager and gets
 * the response based on inputs from the user.
 */
public class PublisherAgent implements Publisher{

    public static JSONRPC2Session mySession = null;
    public static int requestID = 0;

    /*
     * create a session(with the EventManager) object and assign it to a static variable.
     */
    public void createEMConnection(String eventManagerHostname, int eventManagerPort){
        URL serverURL = null;

        try {
            serverURL = new URL("http://"+eventManagerHostname+":"+eventManagerPort);
        } catch (MalformedURLException e) {
            System.out.println("EM not up.");
        }

        mySession = new JSONRPC2Session(serverURL);
    }

    /*
     * take login request from the user and send it to EventManager to create a publisherLogin.
     */
    public void login(String publisherID) throws UnknownHostException {
        String method = "publisherLogin";
        String ipAddress = InetAddress.getLocalHost().getHostAddress();
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("publisherID", publisherID);
        myParams.put("ipAddress", ipAddress);
        request.setNamedParams(myParams);

        // Send request and populate response from EM.
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response != null && response.indicatesSuccess()) {
            System.out.println();
            System.out.println("------" + response.getResult().toString() + " --------");
            System.out.println();
        }else {
            System.out.println("Couldn't login " + publisherID);
        }

    }

    /*
     * checkForPendingNotifcations is called right after logging in to see if the publisher has any pending
     * notifications.
     */
    public void checkForPendingNotifications(String publisherID) {
        String method = "checkForPendingNotifications";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("machineID", publisherID);
        request.setNamedParams(myParams);

        // Send request to EM and populate response
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response == null || !response.indicatesSuccess()) {
            System.out.println("Couldn't check for pending notifications for " + publisherID);
        }else {
            System.out.println(response.getResult());
        }

    }

    /*
     * take logoff from the user and send it to EM to mark publisher as logged off.
     */
    public void logoff(String publisherID) {
        String method = "publisherLogoff";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("publisherID", publisherID);
        request.setNamedParams(myParams);

        // Send logoff request and populate response
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response != null && response.indicatesSuccess()) {
            System.out.println();
            System.out.println("------" + response.getResult().toString() + " --------");
            System.out.println();
        }
        else
            System.out.println("Couldn't logoff " + publisherID);

    }

    /*
     * take inputs from user and create an Event Object. Send that event object to EM, which then publishes that
     * event to all it's topic subscribers.
     */
    @Override
    public void publish(Event event, String qos, int retrails) {
        String method = "publishEvent";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("event", event);
        myParams.put("qos", qos);
        myParams.put("retrails", retrails);
        request.setNamedParams(myParams);

        // Send Event object to EM and populate response.
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response !=null && response.indicatesSuccess()) {
            System.out.println(response.getResult());
        }
    }

    /*
     * This method helps to retrieve Topic object from the EventManager from topic name.
     */
    public Topic getTopicFromTopicName(String topicName){
        String method = "getTopicFromTopicName";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("topicName", topicName);
        request.setNamedParams(myParams);

        // Send request using mySession and populate response.
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response != null && response.indicatesSuccess()){
            Gson gson = new Gson();
            return gson.fromJson(response.getResult().toString(), Topic.class);
        }
        else
            System.out.println("Couldn't get Topic object from name "+ topicName);
        return null;
    }

    /*
     * this method helps to retrieve all available topics from the Event Manager.
     */
    public HashMap<String,Topic> getAllTopics(){
        String method = "getAllTopics";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        // Send getAllTopics request to the EM and get response.
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response != null && response.indicatesSuccess()){
            Gson gson = new Gson();
            Type mapType = new TypeToken<HashMap<String, Topic>>(){}.getType();
            return gson.fromJson(response.getResult().toString(), mapType);
        }
        else
            System.out.println("Couldn't get all topics from EventManager.");
        return null;
    }

    /*
     * advertise method is used to send a Topic to the Event Manager, which then advertises it to all publishers
     * & subscribers
     */
    @Override
    public void advertise(Topic newTopic) {
        String method = "advertiseTopic";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("topic", newTopic);
        request.setNamedParams(myParams);

        // Send advertiseTopic request to Event Manager and populate response.
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response != null && response.indicatesSuccess()) {
            System.out.println(newTopic.getName() + response.getResult());
        }

    }

    /*
     * Listen to requests from Event Manager on port 6969.
     */
    public void listenToNotificationsFromEM() throws IOException {
        ServerSocket listener = new ServerSocket(6969);
        try {
            while (true) {
                new PHandler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    /*
     * helper function to check if a string is numeric.
     */
    public static boolean isNumeric(String str) {
        try {
            Integer.parseInt(str);
            return true;
        } catch(NumberFormatException e){
            return false;
        }
    }

    /*
     * Main is used to handle CLI, take inputs from users and call appropriate functions
     * and create threads to listen to requests from EM.
     */
    public static void main(String[] args) throws UnknownHostException {
        PublisherAgent aPublisher = new PublisherAgent();
        new Thread(() -> {
            try {
                aPublisher.listenToNotificationsFromEM(); //do we want one or multiple instances running?
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        Scanner sc = new Scanner(System.in);
        requestID = new Random().nextInt(5000);
        System.out.println("Please enter your Username to start");
        String publisherID = sc.nextLine().trim();
        while (publisherID.isEmpty()){
            System.out.println("Username cannot be empty. Please enter a unique Username.");
            publisherID = sc.nextLine().trim();
        }

        aPublisher.createEMConnection(args[0], Integer.parseInt(args[1])); //can be parallelized
        aPublisher.login(publisherID);
        aPublisher.checkForPendingNotifications(publisherID);


        while (true){
            System.out.println("==================================================");
            System.out.println("\nWhat operation do you want to perform? \n 1. Publish an Event \n 2. " +
                    "Advertise a new Topic \n 3. List All Topics \n 4. Logoff\nPlease choose one option (1/2/3/4)\n");
            System.out.println("===================================================");
            String userChoice = sc.nextLine();
            while (!userChoice.equals("1") && !userChoice.equals("2")&& !userChoice.equals("3") && !userChoice.equals("4")){
                System.out.println("Please enter 1 to Publish / 2 to Advertise / 3 to list all topics / 4 to logoff. ");
                userChoice = sc.nextLine();
            }
            if (userChoice.equals("1")){
                System.out.println("Please enter an Event ID or press enter to generate a unique id");
                String eventID = sc.nextLine();
                System.out.println("Please enter the Topic name you want to publish this event to");
                String topicName = sc.nextLine();
                while (topicName.isEmpty()){
                    System.out.println("Topic name cannot be empty. Please enter again.");
                    topicName = sc.nextLine();
                }
                Topic retrievedTopic = aPublisher.getTopicFromTopicName(topicName);
                if (retrievedTopic==null){
                    System.out.println("No such topic exists. Please try again.");
                }else {
                    System.out.println("Please enter a Title for this event");
                    String eventTitle = sc.nextLine();
                    while (eventTitle.isEmpty()){
                        System.out.println("Event title cannot be empty.Please enter again.");
                        eventTitle = sc.nextLine();
                    }
                    System.out.println("Please enter some content for this title");
                    String eventContent = sc.nextLine();
                    Event newEvent;
                    if (eventID.isEmpty()){
                        newEvent = new Event(retrievedTopic, eventTitle, eventContent);
                    }else {
                        newEvent = new Event(eventID, retrievedTopic, eventTitle, eventContent);
                    }
                    System.out.println("Please enter your desired QoS for publishing this event (0/1/2)");
                    String qos = sc.nextLine();
                    while (!qos.equals("0") && !qos.equals("1") && !qos.equals("2")){
                        System.out.println("QoS can only be 0 or 1 or 2");
                        qos = sc.nextLine();
                    }
                    int retrails = 0;
                    if (qos.equals("1") || qos.equals("2")){
                        System.out.println("How many retrails to send the event in case we don't receive ack?");
                        String ret = sc.nextLine();
                        while (!isNumeric(ret)){
                            System.out.println("Number of retrials should be numeric. Please enter again.");
                            ret = sc.nextLine();
                        }
                        retrails = Integer.parseInt(ret);
                    }
                    aPublisher.publish(newEvent, qos, retrails);
                }

            } else if (userChoice.equals("2")){
                System.out.println("Please enter an Topic ID or press enter to generate a unique id.");
                String topicID = sc.nextLine();
                System.out.println("Please enter a name for this Topic");
                String topicName = sc.nextLine();
                while (topicName.isEmpty()){
                    System.out.println("Topic name cannot be empty. Please enter again.");
                    topicName = sc.nextLine();
                }
                System.out.println("Please enter some Keywords you'd like to identify this Topic by. Seperated by a comma");
                String keywordsString = sc.nextLine();
                List<String> newTopicKeywords = Arrays.asList(keywordsString.split(","));
                Topic newTopic;
                if (topicID.isEmpty()){
                    newTopic = new Topic(newTopicKeywords, topicName);
                }else {
                    newTopic = new Topic(topicID, newTopicKeywords, topicName);
                }

                aPublisher.advertise(newTopic);

            } else if (userChoice.equals("3")){

                HashMap<String, Topic> allTopics = aPublisher.getAllTopics();

                if (allTopics.size()!=0) {
                    System.out.println("Here's a list of all topicNames");
                    System.out.println(allTopics.keySet());
                }else{
                    System.out.println("No topics available yet.");
                }
            }else {
                aPublisher.logoff(publisherID);
                System.exit(0);
            }
        }
    }
}
