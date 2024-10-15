package in.xnnyygn.xraft.core.rpc.message;

import java.io.Serializable;

import in.xnnyygn.xraft.core.node.NodeId;

public class RequestVoteResult implements Serializable, RaftMessage {

    private int term;
    private boolean voteGranted;
    private String sourceId;

    public RequestVoteResult(int term, boolean voteGranted, String sourceId) {
        this.term = term;
        this.voteGranted = voteGranted;
        this.sourceId = sourceId;
    }

    public int getTerm() {
        return term;
    }

    public void setTerm(int term) {
        this.term = term;
    }

    public boolean isVoteGranted() {
        return voteGranted;
    }

    public void setVoteGranted(boolean voteGranted) {
        this.voteGranted = voteGranted;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        this.sourceId = sourceId;
    }

    public NodeId getSourceNodeId() {
        return new NodeId(sourceId);
    }

    @Override
    public String toString() {
        return "RequestVoteResult{" + "term=" + term +
                ", voteGranted=" + voteGranted +
                '}';
    }
}
