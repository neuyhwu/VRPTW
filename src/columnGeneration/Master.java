package columnGeneration;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jorlib.frameworks.columnGeneration.branchAndPrice.branchingDecisions.BranchingDecision;
import org.jorlib.frameworks.columnGeneration.io.TimeLimitExceededException;
import org.jorlib.frameworks.columnGeneration.master.AbstractMaster;
import org.jorlib.frameworks.columnGeneration.master.OptimizationSense;
import org.jorlib.frameworks.columnGeneration.master.cutGeneration.CutHandler;
import org.jorlib.frameworks.columnGeneration.util.OrderedBiMap;

import dataStructures.DataHandler;
//import globalParameters.GlobalParameters;
import ilog.concert.IloColumn;
import ilog.concert.IloException;
import ilog.concert.IloNumVar;
import ilog.concert.IloObjective;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import parameters.GlobalParameters;
import pricingAlgorithms.PA_PricingProblem;

/**
 * This is an implementation of the master problem. 
 * 
 * @author nicolas.cabrera-malik
 *
 */
public final class Master extends AbstractMaster<VRPTW, RoutePattern, PA_PricingProblem, VRPTWMasterData> {

	//Cplex objects:
	
	IloCplex cplex; //Cplex instance
	private IloObjective obj; //Objective function
	private IloRange[] satisfyDemandConstr; //Constraint
	
	/**
	 * ArrayList to keep track of the method that created a certain column
	 */
	
	public static ArrayList<Integer> generator;
	
	/**
	 * Number of customers
	 */
	private static int N;
	
	/**
	 * List of paths created by now
	 */
	private static ArrayList<RoutePattern> paths;
	
	/**
	 * ID's of the paths that are part of the current basis
	 */
	public static ArrayList<Integer>basisIndexes;
	
	/**
	 * ID's of the paths that are NOT part of the current basis 
	 */

	public static ArrayList<Integer>ceroRCIndexes;
	
	/**
	 * Array to store the dual variables: real values
	 */
	private static double[] duals;
	

	/**
	 * Dual variables corresponding to the subset row inequalities:
	 */
	private static Hashtable<Integer,Double> duals_subset;
			
	/**
	 * Stores the id of the last path
	 */
	private static int pathN;
	
	/**
	 * Stores a chain of nodes for each path in the pool:
	 */
	
	private static Hashtable<String,Integer> chainPaths;
	
	/** Constructor: receives our data model and a reference to our pricing problem**/
	public Master(VRPTW modelData, PA_PricingProblem pricingProblem, CutHandler<VRPTW,VRPTWMasterData> cutHandler) {
		super(modelData, pricingProblem, cutHandler,OptimizationSense.MINIMIZE);
	}

	/**
	 * This method initializes the master problem
	 * @param num
	 * @param ser
	 */
	public void initializeMaster(int num) {
		
		chainPaths = new Hashtable<String,Integer>();
		N = num;
		pathN = 0;
		paths = new ArrayList<RoutePattern>();
		basisIndexes = new ArrayList<Integer>();
		ceroRCIndexes = new ArrayList<Integer>();
		generator = new ArrayList<Integer>();

		//Initialize the dual variables values:
		
		duals = new double[N+1];

		// Initialize the forbidden arcs:
		
		VRPTW.isForbidden = new int[DataHandler.n+1][DataHandler.n+1];
		for(int i = 0;i<=DataHandler.n;i++) {
			for(int j = 0;j<=DataHandler.n;j++) {
				VRPTW.isForbidden[i][j] = 0;
			}
		}
		
	}
	
	/**
	 * Create an initial solution for the VRPTW.
	 * @param pricingProblem pricing problem
	 * @return Initial solution
	 */
	public List<RoutePattern> getInitialSolution(PA_PricingProblem pricingProblem){
	
		// Initialize the initial solution arraylist:
		
		List<RoutePattern> initSolution=new ArrayList<>();
		
		// We add artificial columns (To ensure feasibility of the RMP).
		
		for(int j = 1;j <= N;j++) {
			int[] pattern=new int[N];
			for(int i=0;i<N;i++) {
				pattern[i] = 0;
			}
			double cost = DataHandler.cost[0][j] + DataHandler.cost[j][0];
			pattern[j-1] = 1;
			ArrayList<Integer>route = new ArrayList<Integer>();
			route.add(0);
			route.add(j);
			route.add(0);
			RoutePattern column=new RoutePattern("Artificial", true, pattern,cost*10,route,pricingProblem,0); //We make them artificial so they stay forever.. in the BAP
			paths.add(column);
			chainPaths.put("("+0+"-"+j+");("+j+"-"+0+")", pathN);
			pathN++;
			initSolution.add(column);
			generator.add(-1);
			
			column = new RoutePattern("Initialization", false, pattern,cost,route,pricingProblem,0); //These are not artificial.. in the BAP
			paths.add(column);
			chainPaths.put("("+0+"-"+j+");("+j+"-"+0+")", pathN);
			pathN++;
			initSolution.add(column);
			generator.add(-1);
		}
		
		//Store the number of columns created on the initialization step
		
		VRPTW.numColumns_iniStep = VRPTW.numColumns;
		
		return initSolution;
	}
	
	/**
	 * Create an initial solution for the PLRP.
	 * Initial solution: Pool of routes created by the MSH.
	 * @param pricingProblem pricing problem
	 * @return Initial solution
	 */
	public List<RoutePattern> getInitialSolution_BAP(PA_PricingProblem pricingProblem){
	
		// Re-initializing the number of columns
		
		VRPTW.numColumns = 0;
		
		// Initialize the initial solution arraylist:
		
		List<RoutePattern> initSolution=new ArrayList<>();
		
		// We add artificial columns (To ensure feasibility of the RMP).
		
		for(int j = 1;j <= N;j++) {
			int[] pattern=new int[N];
			for(int i=0;i<N;i++) {
				pattern[i] = 0;
			}
			double cost = DataHandler.cost[0][j-1] + DataHandler.cost[j-1][0];
			pattern[j-1] = 1;
			ArrayList<Integer>route = new ArrayList<Integer>();
			route.add(0);
			route.add(j);
			route.add(0);
			RoutePattern column=new RoutePattern("Artificial", true, pattern,cost*10,route,pricingProblem,0); //We make them artificial so they stay forever.. in the BAP
			paths.add(column);
			chainPaths.put("("+0+"-"+j+");("+j+"-"+0+")", pathN);
			pathN++;
			initSolution.add(column);
			generator.add(-1);
			
			column = new RoutePattern("Initialization", false, pattern,cost,route,pricingProblem,0); //These are not artificial.. in the BAP
			paths.add(column);
			chainPaths.put("("+0+"-"+j+");("+j+"-"+0+")", pathN);
			pathN++;
			initSolution.add(column);
			generator.add(-1);
		}
		
		//Store the number of columns created on the initialization step
		
			VRPTW.numColumns_iniStep = VRPTW.numColumns;
				
		// Returns the initial solution
		
		return initSolution;
	}
	
	/**
	 * Build a the cplex problem. It builds the MP which is usually a linear program.
	 */
	@SuppressWarnings("deprecation")
	@Override
	protected VRPTWMasterData buildModel() {
		try {
			cplex =new IloCplex(); //Create cplex instance
			cplex.setOut(null); //Disable cplex output
			cplex.setParam(IloCplex.IntParam.Threads, GlobalParameters.THREADS); //Set number of threads that may be used by the cplex
			
			//Define the objective
			obj= cplex.addMinimize();

			//Define constraints (visit all customers exactly once)
			
			satisfyDemandConstr=new IloRange[DataHandler.n];
			for(int i=0; i<DataHandler.n; i++)
				satisfyDemandConstr[i]= cplex.addRange(1,Double.MAX_VALUE, "satisfyDemandFinal_"+(i+1));

			//Define a container for the variables
		} catch (IloException e) {
			e.printStackTrace();
		}

		//Define a container for the variables
		Map<PA_PricingProblem,OrderedBiMap<RoutePattern, IloNumVar>> varMap=new LinkedHashMap<>();
		varMap.put(pricingProblems.get(0),new OrderedBiMap<>());

		//Return a new data object which will hold data from the Master Problem. Since we are not working with inequalities in this example,
		//we can simply return the default.
		return new VRPTWMasterData(cplex,varMap);
	}

	/**
	 * Solve the cplex problem and return whether it was solved to optimality
	 */
	@SuppressWarnings("deprecation")
	@Override
	protected boolean solveMasterProblem(long timeLimit) throws TimeLimitExceededException {
		try {
			//Set time limit
			double timeRemaining=Math.max(1,(timeLimit-System.currentTimeMillis())/1000.0);
			cplex.setParam(IloCplex.DoubleParam.TiLim, timeRemaining); //set time limit in seconds
			//Potentially export the model
			if(config.EXPORT_MODEL) cplex.exportModel(config.EXPORT_MASTER_DIR+"master_"+this.getIterationCount()+".lp");

			//Solve the model
			if(!cplex.solve() || cplex.getStatus()!=IloCplex.Status.Optimal){
				if(cplex.getCplexStatus()==IloCplex.CplexStatus.AbortTimeLim) //Aborted due to time limit
					throw new TimeLimitExceededException();
				else {
					cplex.exportModel("./output/master_"+this.getIterationCount()+".lp");
					throw new RuntimeException("Master problem solve failed! Status: "+ cplex.getStatus());
				}
			}else{
				masterData.objectiveValue= cplex.getObjValue();
				
				//Capture the basic and non-basic variables:
				
				basisIndexes = new ArrayList<Integer>();
				ceroRCIndexes = new ArrayList<Integer>();
				RoutePattern[] routePatterns=masterData.getVarMap().getKeysAsArray(new RoutePattern[masterData.getNrColumns()]);
				IloNumVar[] vars=masterData.getVarMap().getValuesAsArray(new IloNumVar[masterData.getNrColumns()]);
				double[] values= cplex.getValues(vars);
				
				//Iterate over each column and add it to the solution if it has a non-zero value
				for(int i=0; i<routePatterns.length; i++){
					routePatterns[i].value=values[i];
					if(values[i]>=config.PRECISION){
						basisIndexes.add(i);
					}else {
						ceroRCIndexes.add(i);
					}
				}
				
			}
		} catch (IloException e) {
			e.printStackTrace();
		}

		return true;
	}

	/**
	 * Store the dual information required by the pricing problems into the pricing problem object.
	 * This method is invoked after the MP has been solved. 
	 * The dual information required to solve the Pricing Porblem has to be passed.
	 */
	@Override
	public void initializePricingProblem(PA_PricingProblem pricingProblem){
		try {
			
			double[] duals_1 = cplex.getDuals(satisfyDemandConstr);

			for(int i = 0;i < N; i++) {
				
				duals[i+1] = duals_1[i];
				
			}

			pricingProblem.initPricingProblem(duals_1);//,dual_2);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * This method stores the dual variables of the current iteration
	 */
	public void replaceActualDuals() {
		try {
			
			double[] duals_1 = cplex.getDuals(satisfyDemandConstr);

			for(int i = 0;i < N; i++) {
				
				duals[i+1] = duals_1[i];
				
			}
			
			//Subset row inequalities
			
			duals_subset = new Hashtable<Integer,Double>();
			for(SubsetRowInequality subsetRowInequality:masterData.subsetRowInequalities.keySet()) {
				duals_subset.put(subsetRowInequality.id,cplex.getDual(masterData.subsetRowInequalities.get(subsetRowInequality)));
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	
	/**
	 * Function which adds a new column to the cplex problem.
	 * This method is invoked when a Pricing Problem generated a new column.
	 * That column most be added to the MP.
	 */
	@Override
	public void addColumn(RoutePattern column) {
		try {

			//Register column with objective
			IloColumn iloColumn= cplex.column(obj,column.cost);

			//Register column with demand constraint
			for(int i=0; i<N; i++) {
				iloColumn=iloColumn.and(cplex.column(satisfyDemandConstr[i], column.yieldVector[i]));
			}
				
			//Account for the subset row inequalities:
			for(SubsetRowInequality subsetRowInequality:masterData.subsetRowInequalities.keySet()) {
				int cuenta = 0;
				if(!subsetRowInequality.containsRoute(column.id)) {
					for(int i:subsetRowInequality.cutSet) {
						if(column.yieldVector[i-1] > 0) {
							cuenta++;
						}
					}
					if(cuenta >= 2) {
						subsetRowInequality.routes.add(column);
						subsetRowInequality.coefficients.add(cuenta);
						subsetRowInequality.routes_ids.add(column.id);
					}
				}
				if(cuenta >= 2) {
					iloColumn = iloColumn.and(cplex.column(masterData.subsetRowInequalities.get(subsetRowInequality),1));
					
				}
			}
			
			//Create the variable and store it
			IloNumVar var= cplex.numVar(iloColumn, 0, 1, "z_"+","+column.id);
			cplex.add(var);
			masterData.addColumn(column, var);
			paths.add(column);
			
		} catch (IloException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Return the solution, i.e columns with non-zero values in the cplex problem
	 */
	@Override
	public List<RoutePattern> getSolution() {
		List<RoutePattern> solution=new ArrayList<>();
		try {
			RoutePattern[] routePatterns=masterData.getVarMap().getKeysAsArray(new RoutePattern[masterData.getNrColumns()]);
			IloNumVar[] vars=masterData.getVarMap().getValuesAsArray(new IloNumVar[masterData.getNrColumns()]);
			double[] values= cplex.getValues(vars);
			
			//Iterate over each column and add it to the solution if it has a non-zero value
			for(int i=0; i<routePatterns.length; i++){
				routePatterns[i].value=values[i];
				if(values[i]>=config.PRECISION){
					solution.add(routePatterns[i]);
					basisIndexes.add(i);
				}else {
					ceroRCIndexes.add(i);
				}
			}
		} catch (IloException e) {
			e.printStackTrace();
		}
		return solution;
	}

	/**
	 * Return the solution, i.e columns with non-zero values in the cplex problem
	 */
	public List<RoutePattern> getAllColumns() {
		List<RoutePattern> solution=new ArrayList<>();
		try {
			RoutePattern[] routePatterns=masterData.getVarMap().getKeysAsArray(new RoutePattern[masterData.getNrColumns()]);
			IloNumVar[] vars=masterData.getVarMap().getValuesAsArray(new IloNumVar[masterData.getNrColumns()]);
			double[] values= cplex.getValues(vars);

			//Iterate over each column and add it to the solution if it has a non-zero value
			for(int i=0; i<routePatterns.length; i++){
				routePatterns[i].value=values[i];
				solution.add(routePatterns[i]);
			}
		} catch (IloException e) {
			e.printStackTrace();
		}
		return solution;
	}
	/**
	 * Close the cplex problem
	 */
	@Override
	public void close() {
		cplex.end();
	}

	/**
	 * Print the solution if desired
	 */
	@Override
	public void printSolution() {
		System.out.println("Master solution:");
		for(RoutePattern cp : this.getSolution())
			System.out.println(cp);
	}

	/**
	 * Export the model to a file
	 */
	@Override
	public void exportModel(String fileName){
		try {
			cplex.exportModel(config.EXPORT_MASTER_DIR + fileName);
		} catch (IloException e) {
			e.printStackTrace();
		}
	}
	
	/** Checks whether there are any violated inequalities, thereby invoking the cut handler
	 * @return true if violated inqualities have been found (and added to the master problem)
	 */
	@Override
	public boolean hasNewCuts() {
		ArrayList<RoutePattern> solution=new ArrayList<>();
		try {
			RoutePattern[] routePatterns=masterData.getVarMap().getKeysAsArray(new RoutePattern[masterData.getNrColumns()]);
			IloNumVar[] vars=masterData.getVarMap().getValuesAsArray(new IloNumVar[masterData.getNrColumns()]);
			double[] values= cplex.getValues(vars);

			//Iterate over each column and add it to the solution if it has a non-zero value
			for(int i=0; i<routePatterns.length; i++){
				routePatterns[i].value=values[i];
				if(values[i]>=config.PRECISION){
					solution.add(routePatterns[i]);
					//System.out.println(routePatterns[i]);
				}
			}
			
		} catch (IloException e) {
			e.printStackTrace();
		}
		masterData.currentSolution = solution;
		return super.hasNewCuts();
	}

	@Override
	public void branchingDecisionPerformed(@SuppressWarnings("rawtypes") BranchingDecision bd) {
		this.close();
		masterData = this.buildModel();
		cutHandler.setMasterData(masterData); //Inform the cutHandler about the new master model
	}


	@Override
	public void branchingDecisionReversed(@SuppressWarnings("rawtypes") BranchingDecision bd) {
		// No action required
	}

	// Auxiliary methods: 
	
	public static int getN() {
		return N;
	}

	public static void setN(int n) {
		N = n;
	}

	public static ArrayList<RoutePattern> getPaths() {
		return paths;
	}

	public static void setPaths(ArrayList<RoutePattern> paths) {
		Master.paths = paths;
	}

	public static double[] getDuals() {
		return duals;
	}

	public static void setDuals(double[] duals) {
		Master.duals = duals;
	}

	public static int getPathN() {
		return pathN;
	}

	public static void setPathN(int pathN) {
		Master.pathN = pathN;
	}

	public static Hashtable<String, Integer> getChainPaths() {
		return chainPaths;
	}

	public static void setChainPaths(Hashtable<String, Integer> chainPaths) {
		Master.chainPaths = chainPaths;
	}

	
	

	/**
	 * @return the duals_subset
	 */
	public static Hashtable<Integer, Double> getDuals_subset() {
		return duals_subset;
	}
	

	
}
