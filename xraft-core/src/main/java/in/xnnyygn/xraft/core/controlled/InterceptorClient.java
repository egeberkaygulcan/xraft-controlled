package in.xnnyygn.xraft.core.controlled;

import io.javalin.Javalin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.util.NoSuchElementException;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import in.xnnyygn.xraft.core.log.entry.Entry;
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
    private CopyOnWriteArrayList<RaftMessage> messageQueue;
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

    public void init(int interceptorPort, int schedulerPort, NodeImpl node) {
        this.interceptorPort = interceptorPort;
        this.schedulerPort = schedulerPort;
        this.node = node;
        this.messageQueue = new CopyOnWriteArrayList<>();
        this.client = HttpClient.newHttpClient();
        this.app = Javalin.create()
                .post("/schedule", ctx -> {
                    try {
                        RaftMessage message = this.messageQueue.getFirst();
                        this.node.scheduleMessage(message);
                    } catch (NoSuchElementException e) {
                        System.out.println("Message queue empty.");
                    }
                });
    }

    public void run() {
        this.app.start(this.interceptorPort);
    }

    public void sendMessage(RaftMessage message) {
        if (message == null) {
            System.out.println("Null message.");
            return;
        } else {
            this.messageQueue.add(message);
            System.out.println("Intercepted " + message.toString());
            // System.out.println(getMessageString(message));
        }
        
        // Passthrough
        // this.node.scheduleMessage(message);

        String json = getMessageString(message);
        if (json.isEmpty()) {
            this.messageQueue.removeLast();
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:"+this.schedulerPort+"/message"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        
    }

    public void sendEvent(String event) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:"+this.schedulerPort+"/event"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(event))
                .build();

            HttpResponse<String> response = this.client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void registerServer() {
        String reg = String.format("{\"id\":\"\",\"addr\":\"\"}", getNodeId(), "127.0.0.1:"+interceptorPort);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:"+this.schedulerPort+"/replica"))
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
            String msgStr = "{";
            msgStr += "\"type\":\"request_vote_request\",";
            msgStr += "\"from\":\"%s\",";
            msgStr += "\"to\":\"%s\",";
            msgStr += "\"term\":\"%d\",";
            msgStr += "\"last_log_term\":\"%d\",";
            msgStr += "\"last_log_idx\":\"%d\"";
            msgStr += "}";

            return String.format(msgStr, msg.getSourceNodeId().toString(), this.getNodeId(), m.getTerm(), m.getLastLogTerm(), m.getLastLogIndex());

        } else if (message instanceof RequestVoteResult) {
            RequestVoteResult msg = (RequestVoteResult) message;
            String msgStr = "{";
            msgStr += "\"type\":\"request_vote_response\",";
            msgStr += "\"from\":\"%s\",";
            msgStr += "\"to\":\"%s\",";
            msgStr += "\"term\":\"%d\",";
            msgStr += "\"vote_granted\":%b";
            msgStr += "}";

            return String.format(msgStr, msg.getSourceId(), this.getNodeId(), msg.getTerm(), msg.isVoteGranted());
        } else if (message instanceof AppendEntriesRpcMessage) {
            AppendEntriesRpcMessage msg = (AppendEntriesRpcMessage) message;
            AppendEntriesRpc m = msg.get();
            String msgStr = "{";
            msgStr += "\"type\":\"request_vote_response\",";
            msgStr += "\"from\":\"%s\",";
            msgStr += "\"to\":\"%s\",";
            msgStr += "\"term\":\"%d\",";
            msgStr += "\"prev_log_term\":\"%d\",";
            msgStr += "\"prev_log_index\":\"\",";
            msgStr += "\"leader_commit\":\"%d\",";

            String entries = "\"entries\":[";
            int i = 0;
            for (Entry e : m.getEntries()) {
                if (i != 0)
                    entries += ",";
                String entry = String.format("{\"data\":\"%s\",\"term\":\"%d\"}", e.toString(), e.getTerm());
                entries += entry;
                i++;
            }
            entries += "]";
            msgStr += entries;
            

            return String.format(msgStr, 
                            msg.getSourceNodeId().toString(), 
                            this.getNodeId(), 
                            m.getTerm(), 
                            m.getPrevLogTerm(), 
                            m.getPrevLogIndex(),
                            m.getLeaderCommit());
        } else if(message instanceof AppendEntriesResultMessage) {
            AppendEntriesResultMessage msg = (AppendEntriesResultMessage) message;
            AppendEntriesResult m = msg.get();
            String msgStr = "{";
            msgStr += "\"type\":\"append_entries_response\",";
            msgStr += "\"from\":\"%s\",";
            msgStr += "\"to\":\"%s\",";
            msgStr += "\"term\":\"%d\",";
            msgStr += "\"current_idx\":\"%d\",";
            msgStr += "\"success\":%b";
            msgStr += "}";

            return String.format(msgStr, 
                            msg.getSourceNodeId().toString(), 
                            this.getNodeId(), 
                            m.getTerm(), 
                            msg.getRpc().getLastEntryIndex(),
                            m.isSuccess());
        } else {
            return "";
        }
    }

    public String getNodeId() { return this.node.getSelfId(); }
}
