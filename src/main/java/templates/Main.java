package templates;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.util.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.Math;
import java.time.Duration;


public class Main {

    /*
     * We can change the parameters here, but we used the function below (readNumbersFromFile)
     * to change the config of our tests quickly
     */
    public static int N = 3;        // number of processes
    public static float alpha = 0;   // fixed crash probability for our faulty processes  
    public static int f = ((int) Math.ceil(Double.valueOf(N)/2)) - 1; // number of max faulty processes
    public static int ts = 500; //After this time (in ms) the main method picks a non-faulty process and holds every other.

    /*
     * This method reads the parameters from the file "parameters.config" and returns them as an array of integers.
     * The first line of the file should contain the parameters in the following order: N, f, alpha (in %), ts.
     */
    public static int[] readNumbersFromFile(String fileName) throws IOException{
        int[] numbers = new int[4];
        BufferedReader br = new BufferedReader(new FileReader(fileName));
        String line = br.readLine();
        String[] tokens = line.split("\\s+");
        for (int i = 0; i < 4; i++) {
            numbers[i] = Integer.parseInt(tokens[i]);
        }
        br.close();
        return numbers;
    }

    public static void main(String[] args) {
        long startingTime = System.currentTimeMillis();
        try{
            int[] numbers = readNumbersFromFile("parameters.config");   //read the parameters from the file.
            N = numbers[0]; 
            f = numbers[1]; 
            alpha = Float.parseFloat(Integer.toString(numbers[2]))/100.0f;
            ts = numbers[3];
        }
        catch (IOException e) { }

        // Instantiate an actor system
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N={}, f={}, alpha={}, ts={}",N,f,alpha,ts);

        ArrayList<ActorRef> references = new ArrayList<>(); //list of references to all processes
        
        for (int i = 0; i < N; i++) {
            // Instantiate processes
            final ActorRef a = system.actorOf(Process.createActor(i + 1, N, alpha), "" + i);
            references.add(a);
        }

        //give each process a view of all the other processes
        Members m = new Members(references);
        for (ActorRef actor : references) {
            actor.tell(m, ActorRef.noSender());
        }

        for (ActorRef actor : references) {
            actor.tell(new StartTimeMessage(startingTime), ActorRef.noSender()); //send the starting time to all
        }

        Collections.shuffle(references);

        for (int i = 0; i < f; i++) {
            // send crash message for the randomly chosen faulty processes
            references.get(i).tell(new CrashMsg(), ActorRef.noSender());
        }

        for (ActorRef actor : references) {
            // send launch message for the processes
            actor.tell(new LaunchMsg(), ActorRef.noSender());
        }
        try{
            Thread.sleep(ts);}
        catch (InterruptedException e){
        }

        System.out.println("The new LEADER is: p"+references.get(f).path().name());  //we take as leader the First non-faulty process in the shuffled array
        for (int i = 0; i < N; i++) {
            if (i!=f){
                references.get(i).tell(new HoldMsg(), ActorRef.noSender()); //we stop all processes but the leader so it can propose alone
                system.scheduler().scheduleOnce(Duration.ofMillis(50), references.get(i), "unfreeze", system.dispatcher(), null);
            }
        }
        references.get(f).tell(new LaunchMsg(), ActorRef.noSender());
    }
}
