package templates;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.util.*;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.ArrayList;
import java.util.Random;

import java.lang.Object;
import akka.japi.Pair;

public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor
    private final int N;//number of processes
    private final int id;//id of current process
    private Members processes;//other processes' references
    private Integer proposal;
    private int ballot;
    private int readBallot;
    private int imposeBallot;
    private Integer estimate;
    private boolean returnValue = true;
    private int mode;  //0=normal - 1=faultProne - 2=silent
    private float alpha;
    boolean decided = false;
    private boolean freeze = false; //false=process not freezed - true=process freezed
    private int oldAbortBallot;
    private int oldGatherBallot;
    private ArrayList<Pair<Integer,Integer>> states;
    int stateEntryZero=0;
    int nAck=0;
    long startTime = 0;
    long latency = 0;
    
    public Process(int ID, int nb, float alpha) {   /////WE HAVE TO DO GATHER
        N = nb;
        id = ID;
        mode=0;
        this.alpha=alpha;
        ballot = id - N;
        readBallot = 0;
        imposeBallot = id - N;
        estimate = null;
        proposal = null;
        oldAbortBallot = -2*N;
        oldGatherBallot = -2*N;

    }
    
    public String toString() {
        return "Process{" + "id=" + id ;
    }

    /**
     * Static function creating actor
     * 
     */
    public static Props createActor(int ID, int nb, float alpha) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb,alpha);
        });
    }
    
    
    private void ofconsProposeReceived(Integer v) {
        if(freeze) return;
        
        this.returnValue=true;
        this.proposal = v;
        this.ballot+=this.N;

        //log.info("p"+self().path().name()+" proposed propsal value = "+ this.proposal);
        resetStates();

        for (ActorRef actor : processes.references) {
            actor.tell(new ReadMsg(ballot), this.getSelf()); //send [READ, ballot] to everyone
            //log.info("Read ballot " + ballot + " msg: p" + self().path().name() + " -> p" + actor.path().name());
        }
    }
    
    private void readReceived(int newBallot, ActorRef pj) {
            //log.info(self().path().name() + " read received " + self().path().name() );

            if (readBallot>newBallot || imposeBallot>newBallot){
            //log.info("The process " + self().path().name() + " who received the proposal sends ABORT to " + pj.path().name());
            pj.tell(new AbortMsg(newBallot), getSelf());
        } else {
            readBallot = newBallot;
            pj.tell(new GatherMsg(newBallot, imposeBallot, estimate), this.getSelf());
        }


    }
        
    
    public void onReceive(Object message) throws Throwable {

        if(this.mode==2){  //if the p is in silent mode i have to do nothing
            return;
        }

        if(this.mode==1){ //if the p is in the fault-prone mode we verify if it crashes or not
            int alp = (int)(this.alpha*100);

            Random rand = new Random();

            int val = rand.nextInt(100);
            if(val<alp){
                this.mode=2;
                log.info("process " + self().path().name()  + " has Crashed!");
                return;
            }
            
        }
        
          if (message instanceof Members) {//save the system's info
              Members m = (Members) message;
              processes = m;
             // log.info("p" + self().path().name() + " received processes info");
          }
    
          else if (message instanceof ReadMsg) {
              ReadMsg m = (ReadMsg) message;

            //  log.info("p " + self().path().name()  + " has received a READ Message from " + getSender());

              this.readReceived(m.ballot, getSender());
          }
          else if (message instanceof LaunchMsg) {

            LaunchMsg m = (LaunchMsg) message;

            //log.info("p " + self().path().name()  + " has received a LAUNCH Message from " + getSender());

            Random rand = new Random();
            int v = rand.nextInt(2);
            this.ofconsProposeReceived(v);
            
          }
          else if (message instanceof ImposeMsg){

            ImposeMsg m = (ImposeMsg) message;

           // log.info("p " + self().path().name()  + " has received an IMPOSE Message from " + getSender());

            this.receivedImpose(m.ballot, m.proposal, getSender());

          }
          else if (message instanceof HoldMsg){

            HoldMsg m = (HoldMsg) message;

            log.info("p " + self().path().name()  + " has received a HOLD Message from " + getSender());
            this.freeze = true;

          }
          else if (message instanceof GatherMsg){

            GatherMsg m = (GatherMsg) message;

           // log.info("p " + self().path().name()  + " has received a GATHER Message from " + getSender());

            this.receivedGather(m.ballot, m.imposeBallot, m.estimate, getSender());
            

          }
          else if (message instanceof DecideMsg){

            DecideMsg m = (DecideMsg) message;

          //  log.info("p " + self().path().name()  + " has received a DECIDE Message from " + getSender());
            
            this.receivedDecide(m.v);

          }
          else if (message instanceof CrashMsg){

            CrashMsg m = (CrashMsg) message;

           // log.info("process " + self().path().name()  + " has received a CRASH Message from " + getSender());
            this.mode=1;

          }
          else if (message instanceof AckMsg){

            AckMsg m = (AckMsg) message;

           // log.info("process " + self().path().name()  + " has received an ACK Message from " + getSender());
            
            this.receivedAck(m.ballot);

          }
          else if (message instanceof AbortMsg){

            AbortMsg m = (AbortMsg) message;
           // log.info("process " + self().path().name()  + " has received an ABORT Message from " + getSender());
            this.receivedAbort(m.ballot);

          }

          else if (message instanceof StartTimeMessage){

            StartTimeMessage m = (StartTimeMessage) message;
            this.startTime=m.start;

          }

          else if (message instanceof String){
            this.freeze = false;
          }
}



private void receivedImpose(int newBallot, Integer v, ActorRef pj){

  if (this.readBallot > newBallot || this.imposeBallot > newBallot) {
            pj.tell(new AbortMsg(newBallot), this.getSender());
        } else {
            this.estimate = v;
            this.imposeBallot = newBallot;
            pj.tell(new AckMsg(newBallot), this.getSender());
        }
}



private void receivedGather(int newBallot, int estBallot, Integer estimate, ActorRef pj){

  states.set(Integer.valueOf(pj.path().name()), new Pair<Integer,Integer>(estimate, estBallot));
  
  if(estBallot==0) {
    stateEntryZero=1;   //if it is zero the stateSize method doesn't count it
  }


  


  int sizeOfStates = this.statesSize(states) + stateEntryZero;

  if (sizeOfStates>(N/2) && oldGatherBallot!=newBallot){ //majority
            oldGatherBallot = newBallot;
            int maxBallot = 0;
            for (int i = 0; i < states.size(); i++) {
                if (states.get(i).second() > maxBallot) {
                    maxBallot = states.get(i).second();
                    proposal = states.get(i).first();
                }
            }

            for (ActorRef actor : processes.references) {
                actor.tell(new ImposeMsg(ballot, proposal), this.getSelf());

             }
        }

}

private void receivedDecide(Integer v){
  if(!this.decided) {
    this.decided = true;

    this.latency = System.currentTimeMillis() - this.startTime;

    log.info("p"+self().path().name()+" decided with LATENCY " + this.latency);

    for (ActorRef actor: processes.references) {
      actor.tell(new DecideMsg(proposal), this.getSelf());   //send IMPOSE to all
    }
    
    this.mode = 2; //silent mode

  }
}

private void receivedAck(int ballot){

   this.nAck++;

        if (this.nAck>(N/2)){
            this.nAck = 0;  //reset for new messages

           // log.info("p"+self().path().name()+" received ACK from Majority" + " (b="+ ballot + ")");

            for (ActorRef actor : processes.references) {
                actor.tell(new DecideMsg(proposal), this.getSelf());    //send DECIDE to all
            }
        }
}

private void receivedAbort(int ballot){

  if(ballot != this.oldAbortBallot && !this.decided){
      this.oldAbortBallot = ballot;
      this.returnValue=false;
  }

}

private int statesSize(ArrayList<Pair<Integer,Integer>> array){  
        int n = 0;
        for(Pair<Integer,Integer> s : array){
            if (s.second()!=0){  //once the first majority is reached i re-initialize the whole array as (null,0)
                n++;
            }
        }
        return n;
    }


private void resetStates(){

  this.stateEntryZero = 0;
  this.states = new ArrayList<Pair<Integer,Integer>>();
  for (int i = 0; i < N; i++) {     // erase the states array
    states.add(new Pair<Integer,Integer>(null, 0));   
  }
  
}


}
