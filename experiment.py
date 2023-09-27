#Requires program to be compiled
import subprocess
import pickle
#As a baseline, you can consider N = 3, 10, 100 (with f = 1, 4, 49, respectively), tle = 0.5s, 1s, 1.5s, 2s, α = 0,0.1,1.

N = [[3,1],[10,4],[100,49],[500,249],[1000,499]]
tle = [500,1000,1500,2000]
alpha = [0,0.1,1]

#process output of our program
def process_output(txt):
    return txt


#how the latency depends on N (for a fixed tle)
#3D ARRAY EXP[tle][N][alpha]
def run_experiment():
    EXP = []
    for t in tle:
        EXP2 = []
        for n in N:
            EXP3 = []
            for a in alpha:
                results = []
                for _ in range(5): #run experiment 5 times
                    r = subprocess.run(["mvn","exec:exec"],shell = True)
                    results.append(process_output(r.stdout))
                result = sum(results)/len(results) #Get the average value.
                EXP3.append(result)
            EXP2.append(EXP3)
        EXP.append(EXP2)
    pickle.dump(EXP, open( "experiment_results.p", "wb"))
    print("Sucessfully saved experiment at 'experiment_results.p'")

def load_experiment():
    return pickle.load(open("experiment_results.p","rb"))

if __name__ == '__main__':
    run_experiment()
    print(load_experiment())