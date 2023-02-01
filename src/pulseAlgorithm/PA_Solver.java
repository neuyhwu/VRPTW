package pulseAlgorithm;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import org.jorlib.frameworks.columnGeneration.branchAndPrice.branchingDecisions.BranchingDecision;
import org.jorlib.frameworks.columnGeneration.io.TimeLimitExceededException;
import org.jorlib.frameworks.columnGeneration.pricing.AbstractPricingProblemSolver;

import branchAndPrice.FixArc;
import branchAndPrice.RemoveArc;
import columnGeneration.Master;
import columnGeneration.VRPTW;
import dataStructures.DataHandler;
import metaheuristics.TabuSearch;
import parameters.CGParameters;
import columnGeneration.RoutePattern;

/**
 * This class provides a solver for the PLRP pricing problem.
 * The pricing problem is an Elementary shortest path with resource constraints, replenishment and park-and-loop
 * 
 */

/**
 * The AbstractPricingProblem serves as a container to hold the dual information coming from the MP. 
 * It defines a Pricing Problem which can be solved by some algorithm. 
 * In our case, the Pricing Problem class maintains the dual values.
 */

public final class PA_Solver extends AbstractPricingProblemSolver<VRPTW, RoutePattern, PA_PricingProblem> {

	/**
	 * Stores the objective function of the pricing problem (with the st dual variables)
	 */
	public static double objectiveFunction;
	
	/**
	 * Was it solved to optimality or not? (we stop the pulse soon sometimes..)
	 */
	public static boolean solvedToOptimality;
	
	/**
	 * Creates a pricing problem solver
	 * @param dataModel
	 * @param pricingProblem
	 */
	public PA_Solver(VRPTW dataModel, PA_PricingProblem pricingProblem) {
		super(dataModel, pricingProblem);
		this.name="PulseAlgorithm"; //Set a name for the solver
		this.buildModel();
	}

	/**
	 * Runs the pulse
	 */
	private void buildModel(){
		
		//Empty..
	}

	
	/**
	 * This method produces zero or more columns. 
	 */
	@Override
	protected List<RoutePattern> generateNewColumns() throws TimeLimitExceededException {
		
		// Initializes the list of patterns:
		
		List<RoutePattern> newPatterns=new ArrayList<>();
		
		
		// Tries to find columns using the tabu search: So far it can only be used if no cuts have been added
		
		try {
			
			if(VRPTW.numInequalities < 1 && VRPTW.cgIteration <= CGParameters.NUM_ITERATIONS_TS) {
			
				// Updates the weights of the arcs :
				
				for(int i = 0;i < DataHandler.numArcs;i++) {
					
					DataHandler.cost[DataHandler.arcs[i][0]][DataHandler.arcs[i][1]] = DataHandler.cost[DataHandler.arcs[i][0]][DataHandler.arcs[i][1]] - Master.getDuals()[DataHandler.arcs[i][0]];
					DataHandler.costList[i] = DataHandler.distList[i]-Master.getDuals()[DataHandler.arcs[i][0]]; //Calculate reduced cost with the dual variable of the tail node of each arc
					
				}	
				
				Master.heuristics.resetPool();
				
				// Calls the tabu search: 
				
				TabuSearch ts = new TabuSearch(Master.heuristics);
				ts.run(Master.basisIndexes, 0);
				
				ArrayList<String> newCols = Master.heuristics.getPoolCols();
				Hashtable<String, Double> colsRC = Master.heuristics.getHeuPoolRC();
				Hashtable<String, Double> colsDist = Master.heuristics.getHeuPoolDist();
				//Hashtable<String, Integer> generators = Master.heuristics.getColsGenerator();
				if(newCols.size()==0){
					System.out.println("Entre aqui, no genera nada");
				}
				for (int i = 0; i < Math.min(5000, newCols.size()); i++) {
					String key = newCols.get(i);
					if(i==0){
					}
					String col = newCols.get(i);
					
					col = col.substring(1,col.length()-1);
					String[] colSplit = col.split(", ");
					ArrayList<Integer> dummyPath = new ArrayList<>();
					for (int j = 0; j < colSplit.length; j++) {
						int node = Integer.parseInt(colSplit[j]);
						dummyPath.add(node);
						
					}
					
					if(colsRC.get(key) < 0) {
						
						int[] pattern=new int[DataHandler.n];
						for(int j=0;j<DataHandler.n;j++) {
							pattern[j] = 0;
						}
						
						for(int j=1;j<dummyPath.size()-1;j++) {
							pattern[dummyPath.get(j)-1] = 1;
						}
						
						//Creates the pattern
						
						RoutePattern column = new RoutePattern("TabuSearch", false, pattern,colsDist.get(key),dummyPath,pricingProblem,colsRC.get(key));
						newPatterns.add(column);
						//System.out.println(dummyPath.toString()+" - "+colsDist.get(key)+" - "+generators.get(key)+" - "+colsRC.get(key));
						
					}
					//pool.add(dummyPath);
					//pDist.add(colsDist.get(key));
					//generator.add(generators.get(key));
				}
				
				// Re-updates the weights of the arcs :
				
				for(int i = 0;i < DataHandler.numArcs;i++) {
					
					DataHandler.cost[DataHandler.arcs[i][0]][DataHandler.arcs[i][1]] = DataHandler.cost[DataHandler.arcs[i][0]][DataHandler.arcs[i][1]] + Master.getDuals()[DataHandler.arcs[i][0]];
					DataHandler.costList[i] = DataHandler.distList[i]+Master.getDuals()[DataHandler.arcs[i][0]]; //Calculate reduced cost with the dual variable of the tail node of each arc
					
				}	
				if(newPatterns.size() > 0) {
					return(newPatterns);
				}
			}
			
			
		}catch(Exception e) {
			e.printStackTrace();
			System.out.println("Problem running the tabu search");
			System.exit(0);
		}
		
		
		// Tries to run the pulse algorithm: 
		
		try {

			//1. Creates and runs the auxiliary problem:
			
			new PulseHandler(Master.getDuals_subset(),Master.getDuals());

			// Compute the list of paths
				
			newPatterns = createListOfElementaryPatterns();
			
			//Assume it was feasible unless we prove otherwise.
			
			this.pricingProblemInfeasible=false;
			
			//3. If solved to optimality.
			
			if(PulseHandler.isWasSolvedToOptimality()) {
				this.pricingProblemInfeasible=false;
				PA_Solver.solvedToOptimality = true;
				PA_Solver.objectiveFunction = PulseHandler.getPrimalBound();	
				this.objective = PulseHandler.getPrimalBound();	
			}else {
				PA_Solver.solvedToOptimality = false;
			}
			
			//If the number of paths was zero, the problem was indeed infeasible!
			
			if(newPatterns.size() == 0) {
				pricingProblemInfeasible=true;
				this.objective=Double.MAX_VALUE;
			}
		}
		catch(Exception e) {
			e.printStackTrace();
			System.out.println("Problem running the pulse");
			System.exit(0);
		}
	
		return newPatterns;
	}
	
	public List<RoutePattern> createListOfElementaryPatterns(){
		
		List<RoutePattern> newPatterns=new ArrayList<>();
		
		//4. Adds all the paths:
		
		for(int i=0;i<PulseHandler.getNumPaths();i++) {
			
			//Create the actual pattern:
			
			int[] pattern=new int[DataHandler.n];
			for(int j=0;j<DataHandler.n;j++) {
				pattern[j] = 0;
			}
			
			//Capture the cost
			
			double cost = PulseHandler.getPathsFound_costs().get(i);

			//Put a 1 in the vector if the customer is there.
			
			for(int j = 1;j<PulseHandler.getPathsFound_customers().get(i).size();j++) {
				if(PulseHandler.getPathsFound_customers().get(i).get(j) <= DataHandler.n) {
					pattern[PulseHandler.getPathsFound_customers().get(i).get(j)-1] = 1;
				}
			}
			
			//Store the reduced cost..this is not necessary it was for a small check.
			double rco = PulseHandler.getPathsFound_reducedCosts().get(i);
				
			//Creates the pattern
				
			RoutePattern column=new RoutePattern("NewPatternPulse", false, pattern,cost,PulseHandler.getPathsFound_routes().get(i),pricingProblem,rco);
				
			//Adds the pattern to the list
				
			newPatterns.add(column);
			
		}
		
		return newPatterns;
	}

	/**
	 * When the Pricing Problem is solved, the set objective function gets invoked first. 
	 */
	@Override
	protected void setObjective() {
		//In this case is empty
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void branchingDecisionPerformed(@SuppressWarnings("rawtypes") BranchingDecision bd) {
		
		if(bd instanceof FixArc) {
			FixArc fixArcDecision = (FixArc) bd;
			if(fixArcDecision.pricingProblem == this.pricingProblem) {
				VRPTW.forbiddenArcs.put(fixArcDecision.keyArc, 1);
				VRPTW.fixedArcs.put(fixArcDecision.keyArc, 1);
				int numD = fixArcDecision.heads_drive.size();
				for(int i = 0;i<numD;i++) {
					VRPTW.isForbidden[fixArcDecision.tails_drive.get(i)][fixArcDecision.heads_drive.get(i)]++;
				}
			}
		}else if(bd instanceof RemoveArc) {
			RemoveArc removeArcDecision = (RemoveArc) bd;
			if(removeArcDecision.pricingProblem == this.pricingProblem) {
				VRPTW.removedArcs.put(removeArcDecision.keyArc, 1);
				int numD = removeArcDecision.heads_drive.size();
				for(int i = 0;i<numD;i++) {
					VRPTW.isForbidden[removeArcDecision.tails_drive.get(i)][removeArcDecision.heads_drive.get(i)]++;
				}
			}
		}
	}

	@Override
	public void branchingDecisionReversed(@SuppressWarnings("rawtypes") BranchingDecision bd) {
		if(bd instanceof FixArc) {
			FixArc fixArcDecision = (FixArc) bd;
			if(fixArcDecision.pricingProblem == this.pricingProblem) {
				VRPTW.forbiddenArcs.remove(fixArcDecision.keyArc);
				VRPTW.fixedArcs.remove(fixArcDecision.keyArc);
				int numD = fixArcDecision.heads_drive.size();
				for(int i = 0;i<numD;i++) {
					VRPTW.isForbidden[fixArcDecision.tails_drive.get(i)][fixArcDecision.heads_drive.get(i)]--;
				}
			}
		}else if(bd instanceof RemoveArc) {
			RemoveArc removeArcDecision = (RemoveArc) bd;
			if(removeArcDecision.pricingProblem == this.pricingProblem) {
				VRPTW.removedArcs.remove(removeArcDecision.keyArc);
				int numD = removeArcDecision.heads_drive.size();
				for(int i = 0;i<numD;i++) {
					VRPTW.isForbidden[removeArcDecision.tails_drive.get(i)][removeArcDecision.heads_drive.get(i)]--;
				}

			}
		}
		
	}
	
	
	
}
