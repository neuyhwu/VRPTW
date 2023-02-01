package columnGeneration;

import java.util.ArrayList;
import java.util.List;
import org.jorlib.frameworks.columnGeneration.colgenMain.ColGen;
import org.jorlib.frameworks.columnGeneration.io.TimeLimitExceededException;
import org.jorlib.frameworks.columnGeneration.master.OptimizationSense;
import org.jorlib.frameworks.columnGeneration.pricing.AbstractPricingProblemSolver;

import pulseAlgorithm.PA_PricingProblem;

/**
 * This method runs the column generation procedure, but with a simple stabilization technique!.
 * @author nicolas.cabrera-malik
 *
 */
public final class ColumnGenerationDirector extends ColGen<VRPTW, RoutePattern, PA_PricingProblem> {

	public ColumnGenerationDirector(VRPTW dataModel, Master master,PA_PricingProblem pricingProblem, List<Class<? extends AbstractPricingProblemSolver<VRPTW, RoutePattern, PA_PricingProblem>>> solvers, List<RoutePattern> initSolution, double cutoffValue,double boundOnMasterObjective, List<PA_PricingProblem> pricingProblems) {
		super(dataModel, master, pricingProblem, solvers, initSolution, cutoffValue, boundOnMasterObjective);
	
	}

	/**
	 * This method generates a lower bound on the objective function of the master problem
	 */
	@Override
	protected double calculateBoundOnMasterObjective(Class<? extends AbstractPricingProblemSolver<VRPTW, RoutePattern, PA_PricingProblem>> solver) {
		return 0.0; //In the current implementation we do not worry about this calculation.
	}

	
	/**
	 * Solve the Column Generation problem. First the master problem is solved. Next the pricing problems(s) is (are) solved. To solve the pricing problems, the pricing
	 * solvers are invoked one by one in a hierarchical fashion. First the first solver is invoked to solve the pricing problems. Any new columns generated are immediately returned.
	 * If it fails to find columns, the next solver is invoked and so on. If the pricing problem discovers new columns, they are added to the master problem and the method continues
	 * with the next column generation iteration.<br>
	 * If no new columns are found, the method checks for violated inequalities. If there are violated inequalities, they are added to the master problem and the method continues with the
	 * next column generation iteration.<br>
	 * The solve procedure terminates under any of the following conditions:
	 * <ol>
	 * <li>the solver could not identify new columns</li>
	 * <li>Time limit exceeded</li>
	 * <li>The bound on the best attainable solution to the master problem is worse than the cutoff value. Assuming that the master is a minimization problem, the Colgen procedure is terminated if {@code ceil(boundOnMasterObjective) >= cutoffValue}</li>
	 * <li>The solution to the master problem is provable optimal, i.e the bound on the best attainable solution to the master problem equals the solution of the master problem.</li>
	 * </ol>
	 * @param timeLimit Future point in time (ms) by which the procedure should be finished. Should be defined as: {@code System.currentTimeMilis()+<desired runtime>}
	 * @throws TimeLimitExceededException Exception is thrown when time limit is exceeded
	 */
	@Override
	public void solve(long timeLimit) throws TimeLimitExceededException{
		
		//set time limit pricing problems
		
		pricingProblemManager.setTimeLimit(timeLimit);
		colGenSolveTime=System.currentTimeMillis();
		

		
		//Initializes the current alpha position and the number of paths:

		int numP = 0;
		boolean foundNewColumns=false; //Identify whether the pricing problem generated new columns
		boolean hasNewCuts; //Identify whether the master problem violates any valid inequalities
		boolean continuar = true;
		notifier.fireStartCGEvent();
		do{
			//if(nrOfColGenIterations == 2) {
			//	System.exit(0);
			//}
			nrOfColGenIterations++;
			VRPTW.cgIteration++;
			hasNewCuts=false;
			
			//For all the remaining iterations:
			
			this.invokeMaster(timeLimit);
			((Master) master).replaceActualDuals();
			
			// Updates the dual variables
			
			((Master) master).replaceActualDuals();

			//Run the pricing problem:
			
			List<RoutePattern> newColumns=this.invokePricingProblems(timeLimit); //List containing new columns generated by the pricing problem
			foundNewColumns=!newColumns.isEmpty();
			numP = newColumns.size();
			if(numP == 0) {
				boundOnMasterObjective = objectiveMasterProblem;
			}
			
			//We can stop when the optimality gap is closed. We still need to check for violated inequalities though.
			
			if(Math.abs(objectiveMasterProblem - boundOnMasterObjective)<config.PRECISION || objectiveMasterProblem <= boundOnMasterObjective ||!foundNewColumns){
				
				//We stop!
				
				continuar = false;
				
				//We call the master problem again: Sometimes a weird error happens with cplex
				
				this.invokeMaster(timeLimit);
				
				if(config.CUTSENABLED){
					long time=System.currentTimeMillis();
					hasNewCuts=master.hasNewCuts();
					masterSolveTime+=(System.currentTimeMillis()-time); //Generating inequalities is considered part of the master problem
					if(hasNewCuts)
						continue;
					else
						break;
				}else
					break;
			}
			
			//Check whether the boundOnMasterObjective exceeds the cutoff value
			if(boundOnMasterExceedsCutoffValue()) {
				this.invokeMaster(timeLimit);
				break;}
			else if(System.currentTimeMillis() >= timeLimit){ //Check whether we are still within the timeLimit
				notifier.fireTimeLimitExceededEvent();
				throw new TimeLimitExceededException();
			}else if(config.CUTSENABLED && !foundNewColumns){ //Check for inequalities. This can only be done if the master problem hasn't changed (no columns can be added).
				this.invokeMaster(timeLimit);
				long time=System.currentTimeMillis();
				hasNewCuts=master.hasNewCuts();
				masterSolveTime+=(System.currentTimeMillis()-time); //Generating inequalities is considered part of the master problem
			}
			
		}while(foundNewColumns || hasNewCuts || continuar);
		this.boundOnMasterObjective = (optimizationSenseMaster == OptimizationSense.MINIMIZE ? Math.max(this.boundOnMasterObjective, this.objectiveMasterProblem) : Math.min(this.boundOnMasterObjective, this.objectiveMasterProblem)); //When solved to optimality, the bound on the master problem objective equals the objective value.
		colGenSolveTime=System.currentTimeMillis()-colGenSolveTime;
		notifier.fireFinishCGEvent();
		VRPTW.numBAPnodes++;
	}

	@Override
	/**
	 * Invokes the solve methods of the algorithms which solve the Pricing Problem. In addition, after solving the Pricing Problems
	 * and before any new columns are added to the Master Problem, this method invokes the {@link #calculateBoundOnMasterObjective(Class solver) calculateBoundOnMasterObjective} method.
	 * @param timeLimit Future point in time by which the Pricing Problem must be finished
	 * @return list of new columns which have to be added to the Master Problem, or an empty list if no columns could be identified
	 * @throws TimeLimitExceededException TimeLimitExceededException
	 */
	protected List<RoutePattern> invokePricingProblems(long timeLimit) throws TimeLimitExceededException {
		//Solve the pricing problem
		List<RoutePattern> newColumns=new ArrayList<>();
		long time=System.currentTimeMillis();
		
		//Update data in pricing problems
		for(PA_PricingProblem pricingProblem : pricingProblems){
			master.initializePricingProblem(pricingProblem);
		}

		//Solve pricing problems in the order of the pricing algorithms
		notifier.fireStartPricingEvent();
		pricingProblemManager.setTimeLimit(timeLimit);
		for(Class<? extends AbstractPricingProblemSolver<VRPTW, RoutePattern, PA_PricingProblem>> solver : solvers){
			newColumns=pricingProblemManager.solvePricingProblems(solver);

			//Calculate a bound on the optimal solution of the master problem
			this.boundOnMasterObjective =(optimizationSenseMaster == OptimizationSense.MINIMIZE ? Math.max(boundOnMasterObjective,this.calculateBoundOnMasterObjective(solver)) : Math.min(boundOnMasterObjective,this.calculateBoundOnMasterObjective(solver)));

			//Stop when we found new columns
			if(!newColumns.isEmpty()){
				break;
			}
		}
		notifier.fireFinishPricingEvent(newColumns);

		pricingSolveTime+=(System.currentTimeMillis()-time);
		nrGeneratedColumns+=newColumns.size();
		//Add columns to the master problem
		if(!newColumns.isEmpty()){
			for(RoutePattern column : newColumns){
				master.addColumn(column);
			}
		}
		return newColumns;
	}

	@Override
	protected boolean boundOnMasterExceedsCutoffValue() {
		if(optimizationSenseMaster == OptimizationSense.MINIMIZE)
			return (boundOnMasterObjective-config.PRECISION) >= cutoffValue;
		else
			return (boundOnMasterObjective+config.PRECISION) <= cutoffValue;
	}



	
}
