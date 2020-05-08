/*
 * @author-name: Rishab Katta.
 *
 * Event Manager is the "centralized server" that handles requests from all the publishers and all the subscribers.
 */
package edu.rit.cs;


import com.google.gson.Gson;
import com.thetransactioncompany.jsonrpc2.*;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2Session;
import com.thetransactioncompany.jsonrpc2.client.JSONRPC2SessionException;
import com.thetransactioncompany.jsonrpc2.server.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.util.*;
import java.net.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/*
 * EventManagerHandler is the wrapper class for all the Handler classes that handle different type of requests from
 * multiple publishers and subscribers.
 */
class EventManagerHandler {

    //Marks Publishers and Subscribers as Logged in / Logged off.
    public static class LoginHandler implements RequestHandler {

        // Reports the method names of the handled requests
        public String[] handledRequests() {
            return new String[]{"publisherLogin", "subscriberLogin", "publisherLogoff", "subscriberLogoff"};
        }

        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            switch (req.getMethod()) {
                case "publisherLogin": {

                    // Obtain publisherID and publisher's IPAddress from the request sent by the publisher.
                    Map<String, Object> myParams = req.getNamedParams();
                    String publisherID = (String) myParams.get("publisherID");
                    String ipAddress = (String) myParams.get("ipAddress");
                    String response;
                    boolean isPublisherInInfoMap = EventManager.PublisherInfo.containsKey(publisherID);
                    for(Map.Entry<String, String> entry : EventManager.PublisherInfo.entrySet()){
                        String pub = entry.getKey();
                        String ipa = entry.getValue();
                        if (ipa.equals(ipAddress)){
                            synchronized (EventManager.PublisherInfo){
                                EventManager.PublisherInfo.replace(pub, "0");
                            }
                        }
                    }
                    synchronized (EventManager.PublisherInfo) {
                        if (isPublisherInInfoMap) {
                            EventManager.PublisherInfo.replace(publisherID, ipAddress);
                            response = "Logged in " + publisherID + ". Welcome back!";
                        } else {
                            EventManager.PublisherInfo.put(publisherID, ipAddress);
                            response = "Registered and logged in " + publisherID;
                        }
                    }
                    return new JSONRPC2Response(response, req.getID());
                }
                case "subscriberLogin": {

                    // Obtain subscriberID and IP Address from the params.
                    Map<String, Object> myParams = req.getNamedParams();
                    String subscriberID = (String) myParams.get("subscriberID");
                    String ipAddress = (String) myParams.get("ipAddress");
                    String response;
                    boolean isSubscriberInInfoMap = EventManager.SubscriberInfo.containsKey(subscriberID);
                    for(Map.Entry<String, String> entry : EventManager.SubscriberInfo.entrySet()){
                        String pub = entry.getKey();
                        String ipa = entry.getValue();
                        if (ipa.equals(ipAddress)){
                            synchronized (EventManager.SubscriberInfo){
                                EventManager.SubscriberInfo.replace(pub, "0");
                            }
                        }
                    }
                    synchronized (EventManager.SubscriberInfo) {
                        if (isSubscriberInInfoMap) {
                            EventManager.SubscriberInfo.replace(subscriberID, ipAddress);
                            response = "Logged in " + subscriberID + ". Welcome back!";
                        } else {
                            EventManager.SubscriberInfo.put(subscriberID, ipAddress);
                            response = "Registered and logged in " + subscriberID;
                        }
                    }
                    return new JSONRPC2Response(response, req.getID());
                }
                case "publisherLogoff": {

                    // Obtain publisherID and publisher's IPAddress from the request sent by the publisher.
                    Map<String, Object> myParams = req.getNamedParams();
                    String hostname = (String) myParams.get("publisherID");
                    boolean isPublisherInInfoMap = EventManager.PublisherInfo.containsKey(hostname);
                    synchronized (EventManager.PublisherInfo) {
                        if (isPublisherInInfoMap) {
                            EventManager.PublisherInfo.replace(hostname, "0");
                        } else {
                            EventManager.PublisherInfo.put(hostname, "0");
                        }
                    }
                    return new JSONRPC2Response(hostname + " logged off.", req.getID());
                }
                case "subscriberLogoff": {

                    // Obtain subscriberID and IPAddress from the request sent by the subscriber.
                    Map<String, Object> myParams = req.getNamedParams();
                    String hostname = (String) myParams.get("subscriberID");
                    boolean isSubscriberInInfoMap = EventManager.SubscriberInfo.containsKey(hostname);
                    synchronized (EventManager.SubscriberInfo) {
                        if (isSubscriberInInfoMap) {
                            EventManager.SubscriberInfo.replace(hostname, "0");
                        } else {
                            EventManager.SubscriberInfo.put(hostname, "0");
                        }
                    }

                    return new JSONRPC2Response(hostname + " logged off.", req.getID());
                }
                default:
                    return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
            }
        }
    }

    //Implements a Handler for publishing of Events.
    public static class PublishHandler implements RequestHandler {

        // Reports the method names of the handled requests
        public String[] handledRequests() {return new String[]{"publishEvent"};}

        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("publishEvent")) {
                Map<String, Object> myParams = req.getNamedParams();
                Gson gson = new Gson();
                Event newEvent = gson.fromJson(myParams.get("event").toString(), Event.class);
                String qos = (String) myParams.get("qos");
                long retrails = (long) myParams.get("retrails");
                synchronized (EventManager.EventInfo) {
                    EventManager.EventInfo.add(newEvent);
                }
                Topic eventTopic = newEvent.getTopic();
                if (!EventManager.TopicInfo.containsKey(eventTopic.getName())) {
                    return new JSONRPC2Response("Topic doesn't exist yet.", req.getID());
                }

                //get all the online subscribers. If subscribers are not online put events in a pending notifications map.
                List<String> subscribersToSendEventTo = EventManager.TopicSubscribers.get(eventTopic.getName());
                HashMap<String, String> onlineSubscribersToSendEventTo = new HashMap<>();
                if (subscribersToSendEventTo != null) {
                    for (String subscriber : subscribersToSendEventTo) {
                        if (!EventManager.SubscriberInfo.get(subscriber).equals("0")) {
                            onlineSubscribersToSendEventTo.put(subscriber, EventManager.SubscriberInfo.get(subscriber));
                        } else {
                            System.out.println("Event couldn't be sent to " + subscriber + ". Added to pending notifications.");
                            if (EventManager.PendingNotifications.containsKey(subscriber)) {
                                synchronized (EventManager.PendingNotifications) {
                                    EventManager.PendingNotifications.get(subscriber).add(newEvent);
                                }
                            } else {
                                synchronized (EventManager.PendingNotifications) {
                                    EventManager.PendingNotifications.put(subscriber, new ArrayList<>(Arrays.asList(newEvent)));
                                }
                            }
                        }
                    }
                }
                //for all online subscribers, send that event. If a subscriber is not online and we try to send
                // it goes to catch block and we add it to pending notifications.
                for (Map.Entry<String, String> entry : onlineSubscribersToSendEventTo.entrySet()) {
                    String subscriber = entry.getKey();
                    String subscriberIP = entry.getValue();
                    URL serverURL = null;
                    try {
                        serverURL = new URL("http://" + subscriberIP + ":" + 6969);

                    } catch (MalformedURLException e) {
                        System.out.println("Subscriber not up.");
                    }
                    JSONRPC2Session mySession = new JSONRPC2Session(serverURL);
                    EventManager.requestID += 1;
                    JSONRPC2Request request = new JSONRPC2Request("receiveEvent", EventManager.requestID);
                    Map<String, Object> advertiseParams = new HashMap<>();
                    advertiseParams.put("event", newEvent);
                    request.setNamedParams(myParams);
                    JSONRPC2Response response = null;
                    try {
                        response = mySession.send(request);
                    } catch (JSONRPC2SessionException e) {
                        if (qos.equals("1") || qos.equals("2")){
                            for (int i=0; i<=retrails; i++){
                                if (response == null){
                                    try {
                                        response = mySession.send(request);
                                    } catch (JSONRPC2SessionException ignored) {}
                                }
                            }
                        }
                    }
                    if (response != null && response.indicatesSuccess())
                        System.out.println("Event successfully sent to " + subscriber);
                    else {
                        if (EventManager.PendingNotifications.containsKey(subscriber)) {
                            synchronized (EventManager.PendingNotifications) {
                                EventManager.PendingNotifications.get(subscriber).add(newEvent);
                            }
                        } else {
                            synchronized (EventManager.PendingNotifications) {
                                EventManager.PendingNotifications.put(subscriber, new ArrayList<>(Arrays.asList(newEvent)));
                            }
                        }
                        System.out.println("Event couldn't be sent to " + subscriber + ". Added to pending notifications.");
                    }
                }

                return new JSONRPC2Response("Event successfully published to all subscribers", req.getID());

            } else {return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());}
        }
    }

    //Implements a Handler for Advertising of all topics.
    public static class AdvertiseHandler implements RequestHandler {

        // Reports the method names of the handled requests
        public String[] handledRequests() {return new String[]{"advertiseTopic"};}


        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("advertiseTopic")) {
                Map<String, Object> myParams = req.getNamedParams();
                Gson gson = new Gson();
                Topic newTopic = gson.fromJson(myParams.get("topic").toString(), Topic.class);

                synchronized (EventManager.TopicInfo) {
                    EventManager.TopicInfo.put(newTopic.getName(), newTopic);
                }

                //get all online subscribers, if not online, put that topic in a pending notifications map.
                HashMap<String, String> onlineClientsToSendTopicTo = new HashMap<>();
                for (String subscriber : EventManager.SubscriberInfo.keySet()) {
                    if (!EventManager.SubscriberInfo.get(subscriber).equals("0")) {
                        onlineClientsToSendTopicTo.put(subscriber, EventManager.SubscriberInfo.get(subscriber));
                    } else {
                        if (EventManager.PendingNotifications.containsKey(subscriber)) {
                            synchronized (EventManager.PendingNotifications) {
                                EventManager.PendingNotifications.get(subscriber).add(newTopic);
                            }
                        } else {
                            synchronized (EventManager.PendingNotifications) {
                                EventManager.PendingNotifications.put(subscriber, new ArrayList<>(Arrays.asList(newTopic)));
                            }
                        }
                    }
                }

                //get all online publishers, if not online, put that topic in a pending notifications map.
                for (String publisher : EventManager.PublisherInfo.keySet()) {
                    if (!EventManager.PublisherInfo.get(publisher).equals("0")) {
                        onlineClientsToSendTopicTo.put(publisher, EventManager.PublisherInfo.get(publisher));
                    } else {
                        if (EventManager.PendingNotifications.containsKey(publisher)) {
                            synchronized (EventManager.PendingNotifications) {
                                EventManager.PendingNotifications.get(publisher).add(newTopic);
                            }
                        } else {
                            synchronized (EventManager.PendingNotifications) {
                                EventManager.PendingNotifications.put(publisher, new ArrayList<>(Arrays.asList(newTopic)));
                            }
                        }
                    }
                }

                //for all online clients send the topic. If a client is assumed online and is not, we add that topic
                //to a pending notifications map in the catch block.
                for (Map.Entry<String, String> entry : onlineClientsToSendTopicTo.entrySet()) {
                    String clientIP = entry.getValue();
                    String client = entry.getKey();
                    URL serverURL = null;
                    try {
                        serverURL = new URL("http://" + clientIP + ":" + 6969);

                    } catch (MalformedURLException e) {
                        System.out.println("client not up.");
                    }
                    JSONRPC2Session mySession = new JSONRPC2Session(serverURL);
                    EventManager.requestID += 1;
                    JSONRPC2Request request = new JSONRPC2Request("receiveTopic", EventManager.requestID);
                    Map<String, Object> advertiseParams = new HashMap<>();
                    advertiseParams.put("topic", newTopic);
                    request.setNamedParams(myParams);
                    JSONRPC2Response response = null;
                    try {
                        response = mySession.send(request);
                    } catch (JSONRPC2SessionException e) {
                        if (EventManager.PendingNotifications.containsKey(client)) {
                            synchronized (EventManager.PendingNotifications) {
                                EventManager.PendingNotifications.get(client).add(newTopic);
                            }
                        } else {
                            synchronized (EventManager.PendingNotifications) {
                                EventManager.PendingNotifications.put(client, new ArrayList<>(Arrays.asList(newTopic)));
                            }
                        }
                    }
                    if (response != null && response.indicatesSuccess())
                        System.out.println("Topic successfully advertised to " + client);
                    else
                        System.out.println("topic couldn't be sent to " + client + ". added to pending notifications");
                }
                return new JSONRPC2Response(" Topic successfully advertised to all clients.", req.getID());
            } else {
                return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
            }
        }
    }

    //Implements a Handler for Handling subscribers subscribing to a topic.
    public static class SubscribeHandler implements RequestHandler {

        // Reports the method names of the handled requests
        public String[] handledRequests() {return new String[]{"subscribeTopic"};}

        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("subscribeTopic")) {
                Map<String, Object> myParams = req.getNamedParams();
                Gson gson = new Gson();
                Topic newTopic = gson.fromJson(myParams.get("topic").toString(), Topic.class);
                String newTopicName = newTopic.getName();
                String subscriberID = (String) myParams.get("subscriberID");
                boolean isTopicNameInTopicSubscribers = EventManager.TopicSubscribers.containsKey(newTopicName);
                ArrayList<String> subscribersList;
                synchronized (EventManager.TopicSubscribers) {
                    if (isTopicNameInTopicSubscribers) {
                        subscribersList = EventManager.TopicSubscribers.get(newTopicName);
                        subscribersList.add(subscriberID);
                    } else {
                        subscribersList = new ArrayList<>();
                        subscribersList.add(subscriberID);
                    }
                    EventManager.TopicSubscribers.put(newTopicName, subscribersList);
                }

                return new JSONRPC2Response(subscriberID + "successfully subscribed to " + newTopicName, req.getID());
            } else {
                return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
            }
        }
    }

    //Implements a Handler for Handling subscriber unsubscribing from one/all topics.
    public static class UnsubscribeHandler implements RequestHandler {

        // Reports the method names of the handled requests
        public String[] handledRequests() {return new String[]{"unsubscribeTopic", "unsubscribeAll"};}


        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {

            if (req.getMethod().equals("unsubscribeTopic")) {
                Map<String, Object> myParams = req.getNamedParams();
                Gson gson = new Gson();
                Topic newTopic = gson.fromJson(myParams.get("topic").toString(), Topic.class);
                String newTopicName = newTopic.getName();
                String subscriberID = (String) myParams.get("subscriberID");
                boolean isTopicNameInTopicSubscribers = EventManager.TopicSubscribers.containsKey(newTopicName);
                synchronized (EventManager.TopicSubscribers) {
                    if (isTopicNameInTopicSubscribers) {
                        EventManager.TopicSubscribers.get(newTopicName).remove(subscriberID);
                    }
                }
                return new JSONRPC2Response(subscriberID + "successfully unsubscribed from " + newTopicName, req.getID());

            } else if (req.getMethod().equals("unsubscribeAll")) {
                Map<String, Object> myParams = req.getNamedParams();
                String subscriberID = (String) myParams.get("subscriberID");
                if (!EventManager.TopicSubscribers.isEmpty()) {
                    synchronized (EventManager.TopicSubscribers) {
                        for (Map.Entry<String, ArrayList<String>> entry : EventManager.TopicSubscribers.entrySet()) {
                            entry.getValue().remove(subscriberID);
                        }
                    }
                }

                return new JSONRPC2Response(subscriberID + "successfully unsubscribed from all topics", req.getID());

            } else {return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());}
        }

    }

    //Implements a Handler for Handling retrieving topic object from topic name
    public static class getTopicFromNameHandler implements RequestHandler {

        // Reports the method names of the handled requests
        public String[] handledRequests() {
            return new String[]{"getTopicFromTopicName", "getTopicFromKeyword", "getAllTopics"};
        }

        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {
            Map<String, Topic> matchedTopics = new HashMap<>();
            Gson gson = new Gson();
            if (req.getMethod().equals("getTopicFromTopicName")) {
                Map<String, Object> myParams = req.getNamedParams();
                String newTopicName = (String) myParams.get("topicName");

                return new JSONRPC2Response(gson.toJson(EventManager.TopicInfo.get(newTopicName)), req.getID());

            } else if (req.getMethod().equals("getTopicFromKeyword")) {
                Map<String, Object> myParams = req.getNamedParams();
                String keyword = (String) myParams.get("keyword");
                for (Map.Entry<String, Topic> entry : EventManager.TopicInfo.entrySet()) {
                    String topicName = entry.getKey();
                    Topic topic = entry.getValue();
                    if (topic.getKeywords().contains(keyword)) {
                        matchedTopics.put(topicName, topic);
                    }
                }

                return new JSONRPC2Response(matchedTopics, req.getID());

            } else if (req.getMethod().equals("getAllTopics")) {

                return new JSONRPC2Response(EventManager.TopicInfo, req.getID());

            } else {return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());}
        }
    }

    //Implements a Handler for Handling sending pending notifications to clients.
    public static class checkForAnyPendingNotificationsHandler implements RequestHandler {

        // Reports the method names of the handled requests
        public String[] handledRequests() {return new String[]{"checkForPendingNotifications"};}

        // Processes the requests
        public JSONRPC2Response process(JSONRPC2Request req, MessageContext ctx) {
            boolean thereArePendingNotifications = false;

            if (req.getMethod().equals("checkForPendingNotifications")) {
                Map<String, Object> myParams = req.getNamedParams();
                String machineID = (String) myParams.get("machineID");
                if (EventManager.PendingNotifications.containsKey(machineID) && EventManager.PendingNotifications.get(machineID).size() > 0) {
                    thereArePendingNotifications = true;
                    String ipAddress = null;
                    if (EventManager.PublisherInfo.containsKey(machineID) && !EventManager.PublisherInfo.get(machineID).equals("0")) {
                        ipAddress = EventManager.PublisherInfo.get(machineID);
                    } else if (EventManager.SubscriberInfo.containsKey(machineID) && !EventManager.SubscriberInfo.get(machineID).equals("0")) {
                        ipAddress = EventManager.SubscriberInfo.get(machineID);
                    }
                    URL serverURL = null;
                    try {
                        serverURL = new URL("http://" + ipAddress + ":6969");

                    } catch (MalformedURLException e) {
                        System.out.println("client not up.");
                    }
                    JSONRPC2Session mySession = new JSONRPC2Session(serverURL);
                    EventManager.requestID += 1;
                    JSONRPC2Request request = new JSONRPC2Request("receivePendingNotifications", EventManager.requestID);
                    Map<String, Object> advertiseParams = new HashMap<>();
                    advertiseParams.put("pendingNotifications", EventManager.PendingNotifications.get(machineID));
                    request.setNamedParams(advertiseParams);
                    JSONRPC2Response response = null;
                    try {
                        response = mySession.send(request);
                    } catch (JSONRPC2SessionException e) {
                        System.err.println(e.getMessage());
                    }
                    if (response.indicatesSuccess()) {
                        System.out.println(response.getResult());
                        synchronized (EventManager.PendingNotifications) {
                            EventManager.PendingNotifications.get(machineID).clear();
                        }
                    } else
                        System.out.println(response.getError().getMessage());
                }
                String resp;
                if (thereArePendingNotifications) {
                    resp = "You have pending notifications.";
                } else {
                    resp = "You don't have any pending notifications.";
                }
                return new JSONRPC2Response(resp, req.getID());
            } else {
                return new JSONRPC2Response(JSONRPC2Error.METHOD_NOT_FOUND, req.getID());
            }
        }
    }
}

/*
 * EventManager is sort of the Main class that calls other classes, based on the requests received from clients.
 */
public class EventManager {

    private static final int EM_MAIN_PORT = 9091;
    public static HashMap<String, String> PublisherInfo = new HashMap<>();
    public static HashMap<String, String> SubscriberInfo = new HashMap<>();
    public static HashSet<Event> EventInfo = new HashSet<>();
    public static HashMap<String, List<Object>> PendingNotifications = new HashMap<>();
    public static HashMap<String, Topic> TopicInfo = new HashMap<>();
    public static HashMap<String, ArrayList<String>> TopicSubscribers = new HashMap<>();
    public static int requestID = 0;


    private static class Handler extends Thread {
        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private Dispatcher dispatcher;

        /**
         * Constructs a handler thread, squirreling away the socket.
         * All the interesting work is done in the run method.
         */
        public Handler(Socket socket) {
            this.socket = socket;

            // Create a new JSON-RPC 2.0 request dispatcher
            this.dispatcher = new Dispatcher();

            // Register all the Handlers with the dispatcher.
            dispatcher.register(new EventManagerHandler.LoginHandler());
            dispatcher.register(new EventManagerHandler.PublishHandler());
            dispatcher.register(new EventManagerHandler.AdvertiseHandler());
            dispatcher.register(new EventManagerHandler.getTopicFromNameHandler());
            dispatcher.register(new EventManagerHandler.SubscribeHandler());
            dispatcher.register(new EventManagerHandler.UnsubscribeHandler());
            dispatcher.register(new EventManagerHandler.checkForAnyPendingNotificationsHandler());

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


    //Start a listener which listens to request from the clients.
    private void startService() throws IOException {
        ServerSocket listener = new ServerSocket(EM_MAIN_PORT);
        try {
            while (true) {
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }

    }

    //list all subscribers subscribed to a topic for EM CLI.
    private void listAllSubscribersForTopic(String topicName) {
        if (EventManager.TopicSubscribers.containsKey(topicName)) {
            System.out.println("list of all the subscribers for that topic: ");
            System.out.print(EventManager.TopicSubscribers.get(topicName));
        } else {
            System.out.println("No subscribers for that topic yet.");
        }
    }

    //list all topics for EM CLI.
    private void listAllAvailableTopics() {
        if (EventManager.TopicInfo.size() == 0) {
            System.out.println("No Topics Yet.");
        }
        for (String topicName : EventManager.TopicInfo.keySet()) {
            System.out.println(topicName);
        }
    }

    //list all subscribers for EM CLI.
    private void listAllSubscribers() {
        if (EventManager.SubscriberInfo.size() == 0) {
            System.out.println("No Subscribers yet.");
        }
        for (Map.Entry<String, String> entry : EventManager.SubscriberInfo.entrySet()) {
            System.out.println(entry.getKey() + "-->" + entry.getValue());
        }
    }

    //main is used handle CLI, instantiate EM and start threads listening to requests from clients in the background.
    public static void main(String[] args) throws IOException {
        EventManager em = new EventManager();
        new Thread(() -> {
            try {
                em.startService();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        Scanner sc = new Scanner(System.in);
        System.out.println("\nEvent Manager is up and running on: " + InetAddress.getLocalHost().getHostAddress() + ":" + EventManager.EM_MAIN_PORT + "\n");
        while (true) {
            System.out.println("====================================================");
            System.out.println("\nWhat do you want to do? \n 1. List all available topics " +
                    "\n 2. List all Subscribers for a particular Topic \n 3. List all Subscribers \nPlease choose one option.\n");
            System.out.println("====================================================");
            String userChoice = sc.nextLine();
            while (!userChoice.equals("1") && !userChoice.equals("2") && !userChoice.equals("3")) {
                System.out.println("Please enter 1 to list all topics / 2 to list all subscribers for a topic / 3 to list all subscribers ");
                userChoice = sc.nextLine();
            }
            if (userChoice.equals("1")) {
                em.listAllAvailableTopics();
            } else if (userChoice.equals("2")) {
                System.out.println("Please enter topic name");
                String topicName = sc.nextLine();
                em.listAllSubscribersForTopic(topicName);
            } else {
                em.listAllSubscribers();
            }
        }

    }


}
