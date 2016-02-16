package tw.org.iii.st.analytics.controller;
public class MipModel1 {
	
}
/*
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.lindo.Lindo;

import tw.org.iii.model.Display;

public class MipModel1 extends Lindo
{
	//-----------------------
	//  variables for LINDO
	//-----------------------
	private int[] nErrorCode = new int[1];
	private StringBuffer cErrorMessage = new StringBuffer(LS_MAX_ERROR_MESSAGE_LENGTH);
	private StringBuffer cLicenseKey = new StringBuffer(LS_MAX_ERROR_MESSAGE_LENGTH);
	private Object pEnv = null;
	private Object pModel = null;
	
	//------------------------------------
	//  variables for data used in MIP
	//------------------------------------
	//given from Scheduling2
	private int numCluster;
	private boolean[][] feasibleAssignment; //row: cluster, column: day
	private int[][] travelTime; //row: cluster, column: cluster
	
	//calculated in this class
	private boolean[][] existingRelation; //row: cluster, column: cluster
	
	//------------------------------------------------
	//  variables for MIP information and solutions
	//------------------------------------------------
	private int numVariableW;
	private int numVariableX;
	private int colbegW;
	private int colbegX;
	private int numVariable;
	private String[] constraintName;
	private String[] variableName;
	private double[] objValue;
	private double[] solution;
	
	//constants
	private final double oneBelow = 0.999;
	private final boolean isDisplay = true;
	
	//----------------------------------------------
	//  constructor / setting up LINDO environment
	//----------------------------------------------
	MipModel1 () {
		if (System.getProperty("os.name").equals("Windows 7"))
			nErrorCode[0] = LSloadLicenseString("C:/Lindoapi/license/lndapi90-trial-hyper.lic", cLicenseKey);
		else
			nErrorCode[0] = LSloadLicenseString("/opt/lindoapi/license/lndapi90_0107.lic", cLicenseKey);
		APIErrorCheck(pEnv);

		pEnv = LScreateEnv(nErrorCode, cLicenseKey.toString());
		APIErrorCheck(pEnv);

		pModel = LScreateModel(pEnv, nErrorCode);		
	}
	
	//----------------------------------------------
	//  LINDO generalized error reporting function
	//----------------------------------------------
	private void APIErrorCheck(Object pEnv) {
		if (0 != nErrorCode[0]) {
			LSgetErrorMessage(pEnv, nErrorCode[0], cErrorMessage);
			System.out.println("\nError " + nErrorCode[0] + ": " + cErrorMessage);
			System.out.println();
			System.exit(1);
	  }
	}
	
	//----------------------------
	//  setting data used in MIP
	//----------------------------
	public void setDataUsedInMip(boolean[][] feasibleAssignment, int[][] travelTime) {
		this.numCluster = feasibleAssignment.length;
		this.feasibleAssignment = feasibleAssignment;
		this.travelTime = travelTime;
	}
	//--------------------------------------------
	//  setting up MIP model and solving the MIP
	//--------------------------------------------
	public int run() {
		
		Date dateLindoStart = new Date(System.currentTimeMillis());
		
		//--------------------
		//   prepare data
		//--------------------
		existingRelation = new boolean[numCluster][numCluster];
		
		List<List<Integer>> rowIndexInW = new ArrayList<List<Integer>>();
		List<List<Integer>> rowIndexInX = new ArrayList<List<Integer>>();
		for (int i = 0; i < numCluster * numCluster; ++i) {
			List<Integer> temp1 = new ArrayList<Integer>();
			rowIndexInW.add(temp1);
			
			List<Integer> temp2 = new ArrayList<Integer>();
			rowIndexInX.add(temp2);
		}
		
		numVariableW = 0;
		for (int c = 0; c < numCluster; ++c)
			for (int d = 0; d < numCluster; ++d)
				if (feasibleAssignment[c][d])
					++numVariableW;
		
		numVariableX = 0;
		int k = 0;
		for (int c1 = 0; c1 < numCluster; ++c1) {
			for (int c2 = 0; c2 < numCluster; ++c2) {
				
				if (c1 == c2)
					continue;
				
				for (int d = 0; d < numCluster - 1; ++d) {
					if (feasibleAssignment[c1][d] && feasibleAssignment[c2][d + 1]) {
						
						if (! existingRelation[c1][c2]) {
							existingRelation[c1][c2] = true;
							++numVariableX;
						}
						rowIndexInW.get(c1 + d * numCluster).add(k);
						rowIndexInW.get(c2 + (d + 1) * numCluster).add(k);
						rowIndexInX.get(c1 + c2 * numCluster).add(k);
						++k;
					}
				}
			}
		}
		int constraintCount = k;
		
	   	if (isDisplay) {
	 		Display.print_2D_Array(existingRelation, "Display existingRelation");
	 		Display.print_2D_Array(feasibleAssignment, "Display feasibleAssignment");
	 		Display.print_2D_List(rowIndexInW, "Display rowIndexInW");	 		
	 		Display.print_2D_List(rowIndexInX, "Display rowIndexInX");
	   	}
		
		//------------------------------------------------
		// index information of constraints and variables
		//------------------------------------------------
	   	int numConstraintClusterAssignment = numCluster;
	   	int numConstraintDayAssignment = numCluster;
	   	int numConstraintRelation1 = constraintCount;
	   	int numConstraintRelation2 = constraintCount;
	   	
	   	int rowbegConstraintClusterAssignment = 0;
	   	int rowbegConstraintDayAssignment = rowbegConstraintClusterAssignment + numConstraintClusterAssignment;
	   	int rowbegConstraintRelation1 = rowbegConstraintDayAssignment + numConstraintDayAssignment;
	   	int rowbegConstraintRelation2 = rowbegConstraintRelation1 + numConstraintRelation1;
	   	int numConstraint = rowbegConstraintRelation2 + numConstraintRelation2;
		System.out.println("numConstraint = " + numConstraint);
		
		System.out.println("numVariableW=" + numVariableW + ", numVariableX=" + numVariableX);
		
		colbegW = 0;
		colbegX = colbegW + numVariableW;
		numVariable = colbegX + numVariableX;
		System.out.println("numVariable = " + numVariable);
		
		constraintName = new String[numConstraint + 1];
		variableName = new String[numVariable + 1];
		
		//--------------------
		//	objective
		//--------------------
		int objDir;
		double objConst = 0;
		double[] objCoef = new double[numVariable];
		
		objDir = LS_MIN;
		k = 0;
		for (int c1 = 0; c1 < numCluster; ++c1) {
			for (int c2 = 0; c2 < numCluster; ++c2) {
				if (existingRelation[c1][c2]) {
					objCoef[colbegX + k] = travelTime[c1][c2];
					++k;
				}
			}
		}
		
		//--------------------------
		// RHS and constraint types
		//--------------------------
		double[] rhs = new double[numConstraint];
		char[] constraintTypeChar = new char[numConstraint];
		
		// cluster assignment
		for (int i = 0; i < numConstraintClusterAssignment; ++i) {
			int idx = rowbegConstraintClusterAssignment + i;
			constraintTypeChar[idx] = 'E';
			rhs[idx] = 1;
			constraintName[idx] = "clusterAssignment_" + String.valueOf(i);
		}
		
		// day assignment
		for (int i = 0; i < numConstraintDayAssignment; ++i) {
			int idx = rowbegConstraintDayAssignment + i;
			constraintTypeChar[idx] = 'E';
			rhs[idx] = 1;
			constraintName[idx] = "dayAssignment_" + String.valueOf(i);
		}
		
		// relation 1
		for (int i = 0; i < numConstraintRelation1; ++i) {
			int idx = rowbegConstraintRelation1 + i;
			constraintTypeChar[idx] = 'L';
			rhs[idx] = 1;
			constraintName[idx] = "relation1_" + String.valueOf(i);
		}
		
		// relation 2
		for (int i = 0; i < numConstraintRelation2; ++i) {
			int idx = rowbegConstraintRelation2 + i;
			constraintTypeChar[idx] = 'G';
			rhs[idx] = 0;
			constraintName[idx] = "relation2_" + String.valueOf(i);
		}
		
		constraintName[constraintName.length - 1] = null;
		String constraintType = new String(constraintTypeChar);
		
		//---------------------
		//  coefficient of A
		//---------------------
		int numNonzero = numVariableW
						+ numVariableW
						+ numConstraintRelation1 * 3
						+ numConstraintRelation2 * 3;
		System.out.println("numNonzero=" + numNonzero);
		
		int[] begIdxEachCol = new int[numVariable + 1];
		double[] ACoef = new double[numNonzero];
		int[] ACoefRowIdx = new int[numNonzero];
		
		begIdxEachCol[0] = 0;
		k = 0; //overall index -- nonzero
		
		//------ variable w ------//
		int varw = 0; //overall index -- variable w
		
		for (int c = 0; c < numCluster; ++c)
			for (int d = 0; d < numCluster; ++d)
				if (feasibleAssignment[c][d]) {
					
					// cluster assignment
					ACoef[k] = 1;
					ACoefRowIdx[k++] = rowbegConstraintClusterAssignment + c;
					
					// day assignment
					ACoef[k] = 1;
					ACoefRowIdx[k++] = rowbegConstraintDayAssignment + d;
					
					// relation 1
					List<Integer> rowIdx = rowIndexInW.get(c + d * numCluster);
					
					for (int i = 0; i < rowIdx.size(); ++i) {
						ACoef[k] = 1;
						ACoefRowIdx[k++] = rowbegConstraintRelation1 + rowIdx.get(i);
					}
					
					// relation 2
					for (int i = 0; i < rowIdx.size(); ++i) {
						ACoef[k] = 1;
						ACoefRowIdx[k++] = rowbegConstraintRelation2 + rowIdx.get(i);
					}
					
					begIdxEachCol[colbegW + varw + 1] = begIdxEachCol[colbegW + varw] + 2 + rowIdx.size() * 2;
					variableName[colbegW + varw] = "w_" + String.valueOf(c) + "_" + String.valueOf(d);
					++varw;
				}
		
		//------ variable x ------//
		int varx = 0; //overall index -- variable x
		
		for (int c1 = 0; c1 < numCluster; ++c1)
			for (int c2 = 0; c2 < numCluster; ++c2)
				if (existingRelation[c1][c2]) {
					
					// relation 1
					List<Integer> rowIdx = rowIndexInX.get(c1 + c2 * numCluster);
					
					for (int i = 0; i < rowIdx.size(); ++i) {
						ACoef[k] = -1;
						ACoefRowIdx[k++] = rowbegConstraintRelation1 + rowIdx.get(i);
					}
					
					// relation 2
					for (int i = 0; i < rowIdx.size(); ++i) {
						ACoef[k] = -2;
						ACoefRowIdx[k++] = rowbegConstraintRelation2 + rowIdx.get(i);
					}
					
					begIdxEachCol[colbegX + varx + 1] = begIdxEachCol[colbegX + varx] + rowIdx.size() * 2;
					variableName[colbegX + varx] = "x_" + String.valueOf(c1) + "_" + String.valueOf(c2);
					++varx;
				}
		
		variableName[variableName.length - 1] = null;
		
		//---------------------
		//	variable type
		//---------------------
		double[] varLowerBound = null;
		double[] varUpperBound = null;
		char[] variableTypeChar = new char[numVariable];
		for (int i = 0; i < numVariableW; ++i)
			variableTypeChar[colbegW + i] = 'B';
		for (int i = 0; i < numVariableX; ++i)
			variableTypeChar[colbegX + i] = 'B';
		String variableType = new String(variableTypeChar);
		
		//--------------------
		//	   model
		//--------------------
		nErrorCode[0] = LSloadLPData(pModel, numConstraint, numVariable, objDir,
				objConst, objCoef, rhs, constraintType, numNonzero, begIdxEachCol,
				null, ACoef, ACoefRowIdx, varLowerBound, varUpperBound);
		APIErrorCheck(pEnv);
		
		LSloadVarType(pModel, variableType);
		
		//for (int i=0; i<constraintName.length; ++i)
		//	System.out.println(constraintName[i]);
		//for (int i=0; i<variableName.length; ++i)
		//	System.out.println(variableName[i]);
		
		nErrorCode[0] = LSloadNameData(pModel, "MyTitle", "MyObj", null, null, null,
				constraintName, variableName, null);
		APIErrorCheck(pEnv);
		
		if (System.getProperty("os.name").equals("Windows 7"))
			nErrorCode[0] = LSwriteLINDOFile(pModel, "C:/Users/wu/Downloads/log_LINDO_1/mip" + dateLindoStart.getTime() + ".ltx");
		
		//--------------------
		//	  solving
		//--------------------
		System.out.println("LINDO model 1, start solving...");
		
		int[] nStatus = new int[1];
		nErrorCode[0] = LSsolveMIP(pModel, nStatus);
		APIErrorCheck(pEnv);
		
		objValue = new double[1];
		solution = new double[numVariable];
		LSgetInfo(pModel, LS_DINFO_MIP_OBJ, objValue);
		LSgetMIPPrimalSolution(pModel, solution);
		System.out.println("nStatus=" + nStatus[0]); //1:optimal 3:infeasible (from testing)
		
		
		Date dateLindoEnd = new Date(System.currentTimeMillis());
		System.out.print("LINDO-run elapsed:");
		System.out.println(dateLindoEnd.getTime() - dateLindoStart.getTime());

		return nStatus[0];
	}
	//------------------------
	//   examining solution
	//------------------------
	public void examineSolution() {
		
		System.out.println("objective=" + objValue[0]);
		for (int i = 0; i < solution.length; ++i) {
			System.out.print(variableName[i] + " ");
			System.out.println(solution[i]);
		}
		
		int counter = 0;
		for (int i = 0; i < numVariableW; ++i)  
			if (solution[colbegW + i] > oneBelow )
				++counter;
		System.out.println("Number of (w==1): " + counter);
		
		counter = 0;
		for (int i = 0; i < numVariableX; ++i)  
			if (solution[colbegX + i] > oneBelow )
				++counter;
		System.out.println("Number of (x==1): " + counter);
	}
	
	//------------------------
	//   extracting solution
	//------------------------
	public void extractSolution()
	{
		int k = 0;
		for (int c = 0; c < numCluster; ++c)
			for (int d = 0; d < numCluster; ++d)
				if (feasibleAssignment[c][d]) {
					if (solution[ colbegW + k] > oneBelow)
						System.out.println("cluster:" + c + ", day:" + d + ", w=1");
					++k;
				}
	}
	
	//------------------------------
	//  clearing LINDO environment
	//------------------------------
	public int endLindoEnvironment() {
		
		nErrorCode[0] = LSdeleteEnv(pEnv);
		return nErrorCode[0];
	}
}
*/
