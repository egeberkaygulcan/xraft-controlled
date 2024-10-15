package in.xnnyygn.xraft.core.controlled;

import io.javalin.Javalin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import in.xnnyygn.xraft.core.log.entry.Entry;
import in.xnnyygn.xraft.core.node.NodeEndpoint;
import in.xnnyygn.xraft.core.node.NodeImpl;
import in.xnnyygn.xraft.core.rpc.message.AppendEntriesResult;
import in.xnnyygn.xraft.core.rpc.message.AppendEntriesResultMessage;
import in.xnnyygn.xraft.core.rpc.message.AppendEntriesRpc;
import in.xnnyygn.xraft.core.rpc.message.AppendEntriesRpcMessage;
import in.xnnyygn.xraft.core.rpc.message.RaftMessage;
import in.xnnyygn.xraft.core.rpc.message.RequestVoteResult;
import in.xnnyygn.xraft.core.rpc.message.RequestVoteRpc;
import in.xnnyygn.xraft.core.rpc.message.RequestVoteRpcMessage;

public class InterceptorClient {

    private static final Logger logger = LoggerFactory.getLogger(InterceptorClient.class);
    
    private static InterceptorClient instance;

    private Javalin app;
    private int interceptorPort;
    private int schedulerPort;
    private HashMap<String, CopyOnWriteArrayList<RaftMessage>> messageQueues;
    private NodeImpl node;


    private HttpClient client;

    private InterceptorClient() {}

    public static InterceptorClient getInstance() {
        if (instance == null) {
            synchronized (InterceptorClient.class) {
                if (instance == null) {
                    instance = new InterceptorClient();
                }
            }
        }
        return instance;
    }

    public void init(int interceptorPort, int schedulerPort, NodeImpl node, Set<NodeEndpoint> nodeEndpoints) {
        this.interceptorPort = interceptorPort;
        this.schedulerPort = schedulerPort;
        this.node = node;
        this.messageQueues = new HashMap<String, CopyOnWriteArrayList<RaftMessage>>();

        for(NodeEndpoint endpoint : nodeEndpoints) {
            messageQueues.put(endpoint.getId().toString(), new CopyOnWriteArrayList<RaftMessage>());
        } 

        this.client = HttpClient.newHttpClient();
        this.app = Javalin.create();
        
        for (String key : messageQueues.keySet()) {
            this.app.post("/schedule_" + key, ctx -> {
                try {
                    // System.out.println("Scheduling message from " + key);
                    RaftMessage message = this.messageQueues.get(key).getFirst();
                    this.node.scheduleMessage(message);
                } catch (NoSuchElementException e) {
                    System.out.println("Message queue empty.");
                } finally {}
            });
            
        }
    }

    public void run() {
        this.app.start(this.interceptorPort);
    }

    public void sendMessage(RaftMessage message) {
        if (message == null) {
            System.out.println("Null message.");
            return;
        } else {
            this.messageQueues.get(message.getSourceNodeId().toString()).add(message);
            // System.out.println("Intercepted " + message.toString());
            // System.out.println(getMessageString(message));
        }
        
        // Passthrough
        // this.node.scheduleMessage(message);

        String json = getMessageString(message);
        // System.out.println(json);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:"+this.schedulerPort+"/message"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        
    }

    public void sendEvent(String event) {
        System.out.println("Sending event: " + event);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:"+this.schedulerPort+"/event"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(event))
                .build();

            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void registerServer() {
        JSONObject json = new JSONObject();
        json.put("id", getNodeId());
        json.put("addr", "localhost:"+interceptorPort);
        String reg = json.toString();

        System.out.println("Registering server: " + reg);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:"+this.schedulerPort+"/replica"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(reg))
                .build();

            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private String getMessageString(RaftMessage message) {
        if (message instanceof RequestVoteRpcMessage) {
            RequestVoteRpcMessage msg = (RequestVoteRpcMessage) message;
            RequestVoteRpc m = msg.get();

            JSONObject json = new JSONObject();
            json.put("type", "request_vote_request");
            json.put("from", msg.getSourceNodeId().toString());
            json.put("to", this.getNodeId());

            JSONObject dataJson = new JSONObject();
            dataJson.put("term", m.getTerm());
            dataJson.put("last_log_term", m.getLastLogTerm());
            dataJson.put("last_log_idx", m.getLastLogIndex());

            json.put("data", dataJson.toString());
            
            return json.toString();

        } else if (message instanceof RequestVoteResult) {
            RequestVoteResult msg = (RequestVoteResult) message;
            int granted = msg.isVoteGranted() ? 1 : 0;

            JSONObject json = new JSONObject();
            json.put("type", "request_vote_response");
            json.put("from", msg.getSourceNodeId().toString());
            json.put("to", this.getNodeId());

            JSONObject dataJson = new JSONObject();
            dataJson.put("term", msg.getTerm());
            dataJson.put("vote_granted", granted);

            json.put("data", dataJson.toString());
            
            return json.toString();
        } else if (message instanceof AppendEntriesRpcMessage) {
            AppendEntriesRpcMessage msg = (AppendEntriesRpcMessage) message;
            AppendEntriesRpc m = msg.get();

            JSONObject json = new JSONObject();
            json.put("type", "append_entries_request");
            json.put("from", msg.getSourceNodeId().toString());
            json.put("to", this.getNodeId());

            JSONObject dataJson = new JSONObject();
            dataJson.put("term", m.getTerm());
            dataJson.put("prev_log_term", m.getPrevLogTerm());
            dataJson.put("prev_log_index", m.getPrevLogIndex());
            dataJson.put("leader_commit", m.getLeaderCommit());

            JSONArray entries = new JSONArray();
            for (Entry e : m.getEntries()) {
                JSONObject entry = new JSONObject();
                entry.put("data", Integer.toString(e.hashCode()));
                entry.put("term", e.getTerm());
                entries.put(entry);
            }
            dataJson.put("entries", entries);

            json.put("data", dataJson.toString());
            
            return json.toString();
        } else if(message instanceof AppendEntriesResultMessage) {
            AppendEntriesResultMessage msg = (AppendEntriesResultMessage) message;
            AppendEntriesResult m = msg.get();
            int success = m.isSuccess() ? 1 : 0;

            JSONObject json = new JSONObject();
            json.put("type", "append_entries_response");
            json.put("from", msg.getSourceNodeId().toString());
            json.put("to", this.getNodeId());

            JSONObject dataJson = new JSONObject();
            dataJson.put("term", m.getTerm());
            dataJson.put("current_idx", msg.getRpc().getLastEntryIndex());
            dataJson.put("success",success);

            json.put("data", dataJson.toString());
            
            return json.toString();
        } else {
            return "";
        }
    }

    public String getNodeId() { return this.node.getSelfId(); }
}
