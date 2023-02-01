package director;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Hashtable;

import columnGeneration.VRPTW;
import columnGeneration.VRPTWSolver;
import dataStructures.DataHandler;
import globalParameters.CGParameters;

/**
 * This class organizes the column generation/bcp procedure.
 * @author nicolas.cabrera-malik
 *
 */
public class Solver {
	
	/**
	 * Instance name
	 */
	
	private String instance;
	
	/**
	 * Type of implementation we will use
	 */
	private int type;
	
	/**
	 * This method creates a solver instance
	 * @param instanceID
	 * @param ty
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public Solver(String dataFile, String instanceType, int instanceID, int ty, int numNodes) throws IOException, InterruptedException {
		
		// Instance identifier
		
		instance = instanceType+"-"+instanceID+"_"+numNodes;
		
		// Creates the data handler:
		
		readDataInfo(dataFile, instanceType, instanceID, numNodes);
		
		// Type of algorithm
		
		type = ty;
		
		// Runs the selected algorithm:
		
		if(type == 1) {
			
			runCG();
			
		}else if(type == 2) {
			runBPC(); // Runs the branch-price-and-cut
		}
		
		
	}
	
	
	/**
	 * This method runs the CG procedure
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void runCG() throws IOException, InterruptedException {
		
		// Starts the clock:
		
			Double ITime = (double) System.nanoTime();	

		// Creates the VRPTW object:
		
			VRPTW cs = new VRPTW();
			
		// Current BAP node
			
			VRPTW.stillAtTheRootNode = true;
			
		// Creates a VRPTW solver
			
			VRPTWSolver pcs = new VRPTWSolver(cs);
			
		// Stops the clock
			
			Double FTime = (double) System.nanoTime();
		
		// Computes the computational time:
			
			Double compTime = ((FTime-ITime)/1000000000);
			
		// Prints the results:
			
			printResults_jorlib(cs,pcs, compTime);
			
			
	}
	
	/**
	 * This method runs the BPC procedure
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void runBPC() throws IOException, InterruptedException {
		
		// Starts the clock:
		
			Double ITime = (double) System.nanoTime();	

		// Creates the VRPTW object:
		
			VRPTW cs = new VRPTW();
			
		// Current BAP node
			
			VRPTW.stillAtTheRootNode = true;
			VRPTW.forbiddenArcs = new Hashtable<String,Integer>();
			
		// Creates a VRPTW solver
			
			branchAndPrice.VRPTWSolver pcs = new branchAndPrice.VRPTWSolver(cs);
			
		// Stops the clock
			
			Double FTime = (double) System.nanoTime();
		
		// Computes the computational time:
			
			Double compTime = ((FTime-ITime)/1000000000);
			
		// Prints the results:
			
			printResults_jorlib_bpc(cs,pcs, compTime);
			
			
	}

	/**
	 * This method prints some important results after the run (Implementation 1)
	 * @param CG
	 * @param msh
	 * @param compTime
	 */
	public void printResults_jorlib(VRPTW CG,VRPTWSolver CGSolver, Double compTime) {
		
		System.out.println("------------------------------------");
		System.out.println("Instance:"+instance);
		System.out.println("Configuration:"+CGParameters.CONFIGURATION);
		System.out.println("UpperBound:"+CGSolver.getUpperBound());
		System.out.println("Iterations:"+CGSolver.getNumberOfIterations());
		System.out.println("NumberOfColumns:"+VRPTW.numColumns);
		System.out.println("NumberOfColumns_IniStep:"+VRPTW.numColumns_iniStep);
		System.out.println("NumberOfColumns_Additional:"+(VRPTW.numColumns - VRPTW.numColumns_iniStep));
		System.out.println("NumberOfCuts:"+VRPTW.numInequalities);
		System.out.println("Computational time:"+(compTime));
		System.out.println("LowerBound:"+CGSolver.getLowerBound());	
		System.out.println("GAP = "+100*((CGSolver.getUpperBound() - CGSolver.getLowerBound())/(CGSolver.getUpperBound())));
		System.out.println("------------------------------------");
		System.out.println("Don't forget to plot your solution on: https://nicolascabrera.shinyapps.io/VRPTW/");
		System.out.println("Your file should be located at: "+globalParameters.GlobalParameters.RESULT_FOLDER+"RMP/");
		System.out.println("Name of the file: Solution-"+instance+"_"+CGParameters.CONFIGURATION+".txt");
		System.out.println("------------------------------------");
		
		String ruta = globalParameters.GlobalParameters.RESULT_FOLDER+"RMP/Summary-"+instance+"_"+CGParameters.CONFIGURATION+".txt";

		PrintWriter pw;
		try {
			pw = new PrintWriter(new File(ruta));
			pw.println("Instance:"+instance);
			pw.println("Configuration:"+CGParameters.CONFIGURATION);
			pw.println("UpperBound:"+CGSolver.getUpperBound());//+CG.getUpperBound());
			pw.println("LowerBound:"+CGSolver.getLowerBound());	
			pw.println("GAP:"+100*((CGSolver.getUpperBound() - CGSolver.getLowerBound())/(CGSolver.getUpperBound())));
			pw.println("Iterations:"+VRPTW.cgIteration);
			pw.println("NumberOfColumns:"+VRPTW.numColumns);
			pw.println("NumberOfColumns_IniStep:"+VRPTW.numColumns_iniStep);
			pw.println("NumberOfColumns_Additional:"+(VRPTW.numColumns - VRPTW.numColumns_iniStep));
			pw.println("NumberOfCuts:"+VRPTW.numInequalities);
			pw.println("ComputationalTime:"+(compTime));
			pw.close();
		}catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * This method prints some important results after the run (Implementation 1)
	 * @param CG
	 * @param msh
	 * @param compTime
	 */
	public void printResults_jorlib_bpc(VRPTW CG,branchAndPrice.VRPTWSolver CGSolver, Double compTime) {
		
		System.out.println("------------------------------------");
		System.out.println("Instance:"+instance);
		System.out.println("Configuration:"+CGParameters.CONFIGURATION);
		System.out.println("UpperBound:"+CGSolver.getUpperBound());
		System.out.println("Iterations:"+CGSolver.getNumberOfIterations());
		System.out.println("NumberOfColumns:"+VRPTW.numColumns);
		System.out.println("NumberOfColumns_IniStep:"+VRPTW.numColumns_iniStep);
		System.out.println("NumberOfColumns_Additional:"+(VRPTW.numColumns - VRPTW.numColumns_iniStep));
		System.out.println("NumberOfCuts:"+VRPTW.numInequalities);
		System.out.println("NumberOfBAPNodes:"+VRPTW.numBAPnodes);
		System.out.println("Computational time:"+(compTime));
		System.out.println("LowerBound:"+CGSolver.getLowerBound());	
		System.out.println("GAP = "+100*((CGSolver.getUpperBound() - CGSolver.getLowerBound())/(CGSolver.getUpperBound())));
		System.out.println("------------------------------------");
		System.out.println("Don't forget to plot your solution on: https://nicolascabrera.shinyapps.io/VRPTW/");
		System.out.println("Your file should be located at: "+globalParameters.GlobalParameters.RESULT_FOLDER+"BPC/");
		System.out.println("Name of the file: Solution-"+instance+"_"+CGParameters.CONFIGURATION+".txt");
		System.out.println("------------------------------------");
		
		String ruta = globalParameters.GlobalParameters.RESULT_FOLDER+"BPC/Summary-"+instance+"_"+CGParameters.CONFIGURATION+".txt";

		PrintWriter pw;
		try {
			pw = new PrintWriter(new File(ruta));
			pw.println("Instance:"+instance);
			pw.println("Configuration:"+CGParameters.CONFIGURATION);
			pw.println("UpperBound:"+CGSolver.getUpperBound());//+CG.getUpperBound());
			pw.println("LowerBound:"+CGSolver.getLowerBound());	
			pw.println("GAP:"+100*((CGSolver.getUpperBound() - CGSolver.getLowerBound())/(CGSolver.getUpperBound())));
			pw.println("Iterations:"+VRPTW.cgIteration);
			pw.println("NumberOfColumns:"+VRPTW.numColumns);
			pw.println("NumberOfColumns_IniStep:"+VRPTW.numColumns_iniStep);
			pw.println("NumberOfColumns_Additional:"+(VRPTW.numColumns - VRPTW.numColumns_iniStep));
			pw.println("NumberOfCuts:"+VRPTW.numInequalities);
			pw.println("ComputationalTime:"+(compTime));
			pw.close();
		}catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Creates the data handler
	 * @param in
	 * @return
	 * @throws IOException
	 */
	public DataHandler readDataInfo(String dataFile, String instanceType, int instanceID, int numNodes) throws IOException {
		
		//1.0 Creates a data handler
		
		DataHandler data = new DataHandler(dataFile,instanceType,instanceID,numNodes,CGParameters.BOUND_STEP_PULSE);
	
		//1.1 Read solomon nodes:
		
		data.readSolomon(numNodes);
		
		//1.2 Returns the data handler
		
		return(data);
	}
	

}
