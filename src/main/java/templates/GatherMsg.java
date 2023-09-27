package templates;
/*
 * Class where we implement our gather message
 */
public class GatherMsg {
    public int ballot;
    public int imposeBallot;
    public Integer estimate;

    public GatherMsg(int ballot, int imposeBallot, Integer estimate){
        this.ballot = ballot;
        this.imposeBallot = imposeBallot;
        this.estimate = estimate;
    }
    
}