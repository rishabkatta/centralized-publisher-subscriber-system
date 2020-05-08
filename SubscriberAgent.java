/*
 * @author-name: Rishab Katta
 *
 * SubscriberAgent Class is used to perform all the operations that can be performed by a Subscriber in a pub-sub model.
 */
package edu.rit.cs;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Error;
import com.thetransactioncompany.jsonrpc2.JSONRPC2ParseException;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Request;
import com.thetransactioncompany.jsonrpc2.JSONRPC2Response;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;
import com.thetransactioncompany.jsonrpc2.server.Dispatcher;
import com.thetransactioncompany.jsonrpc2.server.MessageContext;
import com.thetransactioncompany.jsonrpc2.server.RequestHandler;

import java.io.*;
import java.lang.reflect.Type;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/*
 * SubscriberAgentHandler is the wrapper class for all the handlers that SHandler can process.
 */
class SubscriberAgentHandler {

    // Implements a handler for an "receiveTopic"/"receiveEvent" JSON-RPC methods.
    public static class ReceiveTopicsAndEventsHandler implements RequestHandler {


        // Reports the method names of the handled requests
        public String[] handledRequests() {

            return new String[]{"receiveTopic", "receiveEvent"};
        }


        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("receiveTopic")) {

                // Obtain topic object from myParams from the request sent by EM.
                Map<String, Object> myParams = req.getNamedParams();
                Gson gson = new Gson();
                Topic topic = gson.fromJson(myParams.get("topic").toString(), Topic.class);
                String topicName = topic.getName();

                System.out.println("New Topic Received: " + topicName);

                return new JSONRPC2Response(topicName, req.getID());

            } else if (req.getMethod().equals("receiveEvent")) {

                // Obtain event object from myParams from the request sent by EM.
                Map<String, Object> myParams = req.getNamedParams();
                Gson gson = new Gson();
                Event event = gson.fromJson(myParams.get("event").toString(), Event.class);
                String eventName = event.getTitle();

                System.out.println("New Event Received: " + eventName);

                return new JSONRPC2Response(eventName, req.getID());

            } else {
                return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
            }
        }
    }

    // Implements a handler for "receivePendingNotifications" JSON-RPC method.
    public static class ReceivePendingNotificationsHandler implements RequestHandler {


        // Reports the method names of the handled requests
        public String[] handledRequests() {

            return new String[]{"receivePendingNotifications"};
        }


        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("receivePendingNotifications")) {

                // Obtain a list of Objects/Events from myParams which are obtained from request received from the EM.
                Map<String, Object> myParams = req.getNamedParams();
                Gson gson = new Gson();
                Type listType = new TypeToken<ArrayList<Object>>() {
                }.getType();
                List<Object> pendingNotificationsList = gson.fromJson(myParams.get("pendingNotifications").toString(), listType);

                // Iterate through the list and print notifications.
                for (Object o : pendingNotificationsList) {
                    String topicOrEvent = o.toString();
                    boolean isTopic = false;
                    JsonParser parser = new JsonParser();
                    JsonElement element = parser.parse(topicOrEvent);
                    JsonObject obj = element.getAsJsonObject(); //since you know it's a JsonObject
                    Set<Map.Entry<String, JsonElement>> entries = obj.entrySet();//will return members of your object
                    if (entries.size() == 3) {
                        isTopic = true;
                    }
                    if (isTopic) {
                        Topic pendingTopic = gson.fromJson(o.toString(), Topic.class);
                        System.out.println("New topic Received : " + pendingTopic.getName());
                    } else {
                        Event pendingEvent = gson.fromJson(o.toString(), Event.class);
                        System.out.println("New Event Received : " + pendingEvent.getTitle());
                    }
                }

                return new JSONRPC2Response("Pending Notifications Received.", req.getID());

            } else { return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());}
        }
    }
}

/*
 * SHandler is new thread spawned for every request from the EM. It maintains a "registry" of all the Handlers and
 * calls them appropriately.
 */
class SHandler extends Thread {
    private String name;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Dispatcher dispatcher;

    /**
     * Constructs a handler thread, squirreling away the socket.
     * All the interesting work is done in the run method.
     */
    public SHandler(Socket socket) {
        this.socket = socket;

        // Create a new JSON-RPC 2.0 request dispatcher
        this.dispatcher = new Dispatcher();

        // Register the "receiveTopic" "receiveEvent", and "receivePendingNotifications" handlers with it
        dispatcher.register(new SubscriberAgentHandler.ReceiveTopicsAndEventsHandler());
        dispatcher.register(new SubscriberAgentHandler.ReceivePendingNotificationsHandler());

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
            }
        }
    }
}

/*
 * SubscriberAgent is the sort of Main class which calls other classes. It makes requests to the EventManager and gets
 * the response based on inputs from the user. It also creates threads to listen to requests from EM in the background.
 */
public class SubscriberAgent implements Subscriber {

    public static JSONRPC2Session mySession = null;
    public static int requestID = 0;

    // creates a session object by connecting to EM and assigns it to a static variable.
    public void createEMConnection(String eventManagerHostname, int eventManagerPort) {
        URL serverURL = null;

        try {
            serverURL = new URL("http://" + eventManagerHostname + ":" + eventManagerPort);
        } catch (MalformedURLException e) {
            System.out.println("EM not up.");
        }

        mySession = new JSONRPC2Session(serverURL);
    }

    //Takes login request from the user and sends it to EM and mark the subscriber as logged in.
    public void login(String subscriberID) throws UnknownHostException {
        String method = "subscriberLogin";
        String ipAddress = InetAddress.getLocalHost().getHostAddress();
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("subscriberID", subscriberID);
        myParams.put("ipAddress", ipAddress);
        request.setNamedParams(myParams);

        // Send login request to EM and populate response.
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response != null && response.indicatesSuccess()) {
            System.out.println();
            System.out.println(" ----------" + response.getResult().toString() + " -----------");
            System.out.println();
        }
        else
            System.out.println("Couldn't login " + subscriberID);
    }

    //checkForPendingNotifications is called right after logging to check if the user has any pending notifications.
    public void checkForPendingNotifications(String subscriberID) throws UnknownHostException {
        String method = "checkForPendingNotifications";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("machineID", subscriberID);
        request.setNamedParams(myParams);

        // Send checkForPendingNotifications request to the EM and print out response.
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response == null || !response.indicatesSuccess()) {
            System.out.println("Couldn't check for pending notifications for " + subscriberID);
        }else {
            System.out.println(response.getResult());
        }

    }

    //takes logoff request from user and marks user as logged off in EM.
    public void logoff(String subscriberID) {
        String method = "subscriberLogoff";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("subscriberID", subscriberID);
        request.setNamedParams(myParams);

        // Send subscriberLogoff request to EM and populate response.
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response != null && response.indicatesSuccess()) {
            System.out.println();
            System.out.println(" ----------" + response.getResult().toString() + " -----------");
            System.out.println();
        }
        else
            System.out.println("Couldn't logoff " + subscriberID);
    }

    //getTopicFromTopicName is used to retrieve topic object from EM using topic Name.
    public Topic getTopicFromTopicName(String topicName) {
        String method = "getTopicFromTopicName";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("topicName", topicName);
        request.setNamedParams(myParams);

        // Send request to EM and populate response.
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response != null && response.indicatesSuccess()) {
            Gson gson = new Gson();
            return gson.fromJson(response.getResult().toString(), Topic.class);
        } else
            System.out.println("Couldn't get Topic from topic name "+ topicName);
        return null;
    }

    //getTopicFromKeyword is used to retrieve a map of topic names, topic objects from EM using a keyword search.
    public HashMap<String, Topic> getTopicsFromKeyword(String keyword) {
        String method = "getTopicFromKeyword";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("keyword", keyword);
        request.setNamedParams(myParams);

        // Send request
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response.indicatesSuccess()) {
            Gson gson = new Gson();
            Type mapType = new TypeToken<HashMap<String, Topic>>() {
            }.getType();
            return gson.fromJson(response.getResult().toString(), mapType);
        } else
            System.out.println(response.getError().getMessage());
        return null;
    }

    //getAllTopics is used to get all the available topics from the Event Manager.
    public HashMap<String, Topic> getAllTopics() {
        String method = "getAllTopics";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        // Send request and populate response.
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response != null && response.indicatesSuccess()) {
            Gson gson = new Gson();
            Type mapType = new TypeToken<HashMap<String, Topic>>() {
            }.getType();
            return gson.fromJson(response.getResult().toString(), mapType);
        } else
            System.out.println("Couldn't get all topics from event manager.");
        return null;
    }

    // subscribe takes a topic and subscriberID as arguments and sends a request to EM to make the subscriber
    // subscribe to that topic.
    @Override
    public void subscribe(Topic topic, String subscriberID) {
        String method = "subscribeTopic";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("subscriberID", subscriberID);
        myParams.put("topic", topic);
        request.setNamedParams(myParams);

        // Send request to EM and populate response.
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response != null && response.indicatesSuccess()) {
            System.out.println(subscriberID + " successfully subscribed to " + topic.getName());
            //write subscribed topicname to a file.
            try (FileWriter f = new FileWriter("/home/rishabh/topicsSubscribedTo.txt", true);
                 BufferedWriter b = new BufferedWriter(f);
                 PrintWriter p = new PrintWriter(b);) {
                p.println(topic.getName());

            } catch (IOException i) {
                i.printStackTrace();
            }
        } else
            System.out.println(subscriberID + "couldn't be subscribed to " + topic.getName());
    }

    //unsubscribe takes topic and subscriberID as arguments and send a request to EM to unsubscribe that subscriber from
    //that topic.
    @Override
    public void unsubscribe(Topic topic, String subscriberID) throws IOException {
        String method = "unsubscribeTopic";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("subscriberID", subscriberID);
        myParams.put("topic", topic);
        request.setNamedParams(myParams);

        // Send request and populate response.
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response != null && response.indicatesSuccess()) {
            //remove unsubscribed topic from file.
            File inputFile = new File("/home/rishabh/topicsSubscribedTo.txt");
            File tempFile = new File("/home/rishabh/myTempFile.txt");
            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
            String lineToRemove = topic.getName();
            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                // trim newline when comparing with lineToRemove
                String trimmedLine = currentLine.trim();
                if (trimmedLine.equals(lineToRemove)) continue;
                writer.write(currentLine + System.getProperty("line.separator"));
            }
            writer.close();
            reader.close();
            boolean successful = tempFile.renameTo(inputFile);
            if (successful) {
                System.out.println(subscriberID + " successfully unsubscribed from " + topic.getName());
            }

        } else
            System.out.println("Couldn't unsubscribe from topic");

    }

    //take the subscriberID and remove it from all subscribed topics.
    @Override
    public void unsubscribe(String subscriberID) throws IOException {
        String method = "unsubscribeAll";
        requestID += 1;
        JSONRPC2Request request = new JSONRPC2Request(method, requestID);

        Map<String, Object> myParams = new HashMap<>();
        myParams.put("subscriberID", subscriberID);
        request.setNamedParams(myParams);

        // Send request
        JSONRPC2Response response = null;

        try {
            response = mySession.send(request);
        } catch (JSONRPC2SessionException e) {
            System.err.println(e.getMessage());
        }

        // Print response result / error
        if (response != null && response.indicatesSuccess()) {
            //empty the topicsSubscribedTo file, because we're unsubscribing from all topics.
            PrintWriter pw = new PrintWriter("/home/rishabh/topicsSubscribedTo.txt");
            pw.close();
            System.out.println("Successfully unsubscribed from all topics.");
        } else
            System.out.println("Couldn't unsubscribe from all topics.");

    }

    //after subscribing to a topic, it's written onto a file. just read that file and print out topics subscribed to.
    @Override
    public void listSubscribedTopics() {
        try {
            List<String> allLines = Files.readAllLines(Paths.get("/home/rishabh/topicsSubscribedTo.txt"));
            if (allLines.size() == 0) {
                System.out.println("You haven't subscribed to any topics yet.");
            }
            for (String line : allLines) {
                System.out.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    //create a listener that listens to requests from EventManager.
    public void listenToNewTopicAdvertisement() throws IOException {
        ServerSocket listener = new ServerSocket(6969);
        try {
            while (true) {
                new SHandler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    //Main is used to handle CLI, take inputs from users and call appropriate functions
    //Also create background thread to handle requests from EventManager.
    public static void main(String[] args) throws IOException {
        SubscriberAgent aSubscriber = new SubscriberAgent();
        new Thread(() -> {
            try {
                aSubscriber.listenToNewTopicAdvertisement(); //do we want one or multiple instances running?
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        Scanner sc = new Scanner(System.in);
        requestID = new Random().nextInt(5000);

        System.out.println("Please enter your username to start.");
        String subscriberID = sc.nextLine();
        while (subscriberID.isEmpty()){
            System.out.println("Subscriber ID can't be empty. Please enter a unique username.");
            subscriberID = sc.nextLine();
        }

        aSubscriber.createEMConnection(args[0], Integer.parseInt(args[1])); //can be parallelized
        aSubscriber.login(subscriberID);
        aSubscriber.checkForPendingNotifications(subscriberID);

        while (true) {
            System.out.println("==================================================");
            System.out.println("\nWhat operation do you want to perform? \n 1. List Subscribed Topics \n 2. " +
                    "Subscribe to a new Topic \n 3. Unsubscribe from a Topic \n 4. Logoff \n Please choose one option (1/2/3/4)\n");
            System.out.println("===================================================");
            String userChoice = sc.nextLine();
            while (!userChoice.equals("1") && !userChoice.equals("2") && !userChoice.equals("3") && !userChoice.equals("4")) {
                System.out.println("Please enter 1 to List subscribed topics or 2 to Subscribe or 3 to Unsubscribe or 4 to logoff");
                userChoice = sc.nextLine();
            }
            if (userChoice.equals("1")) {
                aSubscriber.listSubscribedTopics();
            } else if (userChoice.equals("2")) {
                System.out.println("Do you want to subscribe by: \n 1. Topic Name\n 2. Keyword\n 3. List All ");
                userChoice = sc.nextLine();
                while (!userChoice.equals("1") && !userChoice.equals("2") && !userChoice.equals("3")) {
                    System.out.println("Incorrect option. Please choose 1 -> by topic name 2 -> by keyword2 -> list all");
                }
                if (userChoice.equals("1")) {
                    System.out.println("Please enter Topic name you want to subscribe to");
                    String topicRequestedToSubscribe = sc.nextLine();
                    while (topicRequestedToSubscribe.isEmpty()) {
                        System.out.println("Topic name cannot be empty. Please enter again.");
                        topicRequestedToSubscribe = sc.nextLine();
                    }
                    if (aSubscriber.getTopicFromTopicName(topicRequestedToSubscribe) != null) {
                        Topic topicToSubscribeTo = aSubscriber.getTopicFromTopicName(topicRequestedToSubscribe);
                        aSubscriber.subscribe(topicToSubscribeTo, subscriberID);
                    } else {
                        System.out.println("No such Topic exists. Please try again.");
                    }
                } else if (userChoice.equals("2")) {
                    System.out.println("Please enter the keyword to search for topics");
                    String keywordToSearch = sc.nextLine();
                    HashMap<String, Topic> topicsMap = aSubscriber.getTopicsFromKeyword(keywordToSearch);
                    if (topicsMap.size() != 0) {
                        System.out.println("Here's a list of topicNames with that keyword.");
                        System.out.println(topicsMap.keySet());
                        System.out.println("Please enter a topic name from this list.");
                        String topicName = sc.nextLine();
                        while (topicName.isEmpty()) {
                            System.out.println("Topic name cannot be empty. Please enter again.");
                            topicName = sc.nextLine();
                        }
                        Topic topic = topicsMap.get(topicName);
                        if (topic != null) {
                            aSubscriber.subscribe(topic, subscriberID);
                        } else {
                            System.out.println("No such topic exists. Please try again.");
                        }
                    } else {
                        System.out.println("No topics with that keyword.");
                    }
                } else {
                    HashMap<String, Topic> allTopics = aSubscriber.getAllTopics();
                    if (allTopics.size() != 0) {
                        System.out.println("Here's a list of all topicNames");
                        System.out.println(allTopics.keySet());
                        System.out.println("Please enter a topic name from this list.");
                        String topicName = sc.nextLine();
                        while (topicName.isEmpty()) {
                            System.out.println("Topic name cannot be empty. Please enter again.");
                            topicName = sc.nextLine();
                        }
                        Topic topic = allTopics.get(topicName);
                        if (topic != null) {
                            aSubscriber.subscribe(topic, subscriberID);
                        } else {
                            System.out.println("No Such topic exists.");
                        }
                    } else {
                        System.out.println("No topics available yet.");
                    }
                }
            } else if (userChoice.equals("3")) {
                System.out.println("Do you want to \n 1. unsubscribe from a specific topic " +
                        "\n 2. unsubscribe from all topics");
                userChoice = sc.nextLine();
                while (!userChoice.equals("1") && !userChoice.equals("2")) {
                    System.out.println("incorrect option. please enter 1 to unsubscribe from a topic or 2 to unsubscribe from all");
                    userChoice = sc.nextLine();
                }
                if (userChoice.equals("1")) {
                    System.out.println("Please enter Topic name you want to unsubscribe from");
                    String topicRequestedToUnsubscribe = sc.nextLine();
                    if (aSubscriber.getTopicFromTopicName(topicRequestedToUnsubscribe) != null) {
                        Topic topicToSubscribeTo = aSubscriber.getTopicFromTopicName(topicRequestedToUnsubscribe);
                        aSubscriber.unsubscribe(topicToSubscribeTo, subscriberID);
                    } else {
                        System.out.println("No such topic exists. ");
                    }
                } else {
                    aSubscriber.unsubscribe(subscriberID);
                }

            } else {
                aSubscriber.logoff(subscriberID);
                System.exit(0);
            }
        }
    }
}
