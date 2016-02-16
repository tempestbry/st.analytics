package tw.org.iii.st.analytics.controller;
public class MipModel2 {
	
}
/*
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import com.lindo.Lindo;

import tw.org.iii.model.TourEvent;
import tw.org.iii.model.DailyInfo;
import tw.org.iii.model.Display;
import tw.org.iii.model.Poi;

public class MipModel2 extends Lindo
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
	private DailyInfo dailyInfo;
	private int numPoi;
	private List<Poi> poiList; //dim: numPoi
	private int[][] travelTime; //dim: (numPoi+2) * (numPoi+2)
	
	//------------------------------------------------
	//  variables for MIP information and solutions
	//------------------------------------------------
	private int numVariableW;
	private int numVariableX;
	private int numVariableS;
	private int numVariableT;
	private int numVariableY;
	private int colbegW;
	private int colbegX;
	private int colbegS;
	private int colbegT;
	private int colbegY;
	private int numVariable;
	private int[][] order_to_ij;
	private int[][] ij_to_order;
	private String[] constraintName;
	private String[] variableName;
	private double[] objValue;
	private double[] solution;
	private List<Poi> itineraryPoi;
	
	//constants
	private final double oneBelow = 0.999;
	private final boolean isDisplay = true;
	
	//----------------------------------------------
	//  constructor / setting up LINDO environment
	//----------------------------------------------
	MipModel2() {
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
	public void setDataUsedInMip(DailyInfo dailyInfo, int[][] travelTime) {
		this.dailyInfo = dailyInfo;
		this.numPoi = dailyInfo.getPoiList().size();
		this.poiList = dailyInfo.getPoiList();
		this.travelTime = travelTime;
	}
	//------------------
	private int[][] openHoursStringParser(String str) {
		if(!str.isEmpty() && str.length() % 12 == 0) {
			int numPeriod = str.length() / 12;
			int[][] openHours = new int[numPeriod][2];
			for (int i = 0; i < numPeriod; ++i) {
				openHours[i][0] = Integer.parseInt(str.substring(0 + i * 12, 2 + i * 12)) * 60 + Integer.parseInt(str.substring(3 + i * 12, 5 + i * 12));
				openHours[i][1] = Integer.parseInt(str.substring(6 + i * 12, 8 + i * 12)) * 60 + Integer.parseInt(str.substring(9 + i * 12, 11 + i * 12));
			}
			return openHours;
		}
		else
			return null;
	}
	//--------------------------------------------
	//  setting up MIP model and solving the MIP
	//--------------------------------------------
	public int run1() {
		
		Date dateLindoStart = new Date(System.currentTimeMillis());
		
		//--------------------
		//   prepare data
		//--------------------
		// open hours
		List<ArrayList<Integer>> openHoursList = new ArrayList<ArrayList<Integer>>();
		
		ArrayList<Integer> tmpList1 = new ArrayList<Integer>(3);
		tmpList1.add(0);
		tmpList1.add(dailyInfo.getStartTimeInMinutes());
		tmpList1.add(dailyInfo.getEndTimeInMinutes());
		openHoursList.add(tmpList1);

		
		for (int i = 0; i < numPoi; ++i) {
			
			// openHoursString = null or 00:00-24:00;
			String openHoursString = poiList.get(i).getOpenHoursOfThisDay();
			if (openHoursString == null || openHoursString.equals("00:00-24:00;")) {
		 	ArrayList<Integer> tmpList0 = new ArrayList<Integer>(3);
		 	tmpList0.add(i + 1);
		 	tmpList0.add(dailyInfo.getStartTimeInMinutes());
		 	tmpList0.add(dailyInfo.getEndTimeInMinutes());
		 	openHoursList.add(tmpList0);
		 	continue;
			}
			
			// openHoursString parsed as null
			int[][] openHoursInMinutes = openHoursStringParser(openHoursString);
			if (openHoursInMinutes == null) {
		 	ArrayList<Integer> tmpList0 = new ArrayList<Integer>(3);
		 	tmpList0.add(i + 1);
		 	tmpList0.add(dailyInfo.getStartTimeInMinutes());
		 	tmpList0.add(dailyInfo.getEndTimeInMinutes());
		 	openHoursList.add(tmpList0);
		 	continue;
			}
			
			// openHoursString in correct format
			for (int j = 0; j < openHoursInMinutes.length; ++j) {
				ArrayList<Integer> tmpList2 = new ArrayList<Integer>(3);
				tmpList2.add(i + 1);
				tmpList2.add(openHoursInMinutes[j][0]);
				if (openHoursInMinutes[j][1] > openHoursInMinutes[j][0])
					tmpList2.add(openHoursInMinutes[j][1]);
				else
					tmpList2.add(openHoursInMinutes[j][1] + 1440);
				openHoursList.add(tmpList2);
			}
		}
		
		ArrayList<Integer> tmpList3 = new ArrayList<Integer>(3);
		tmpList3.add(numPoi + 1);
		tmpList3.add(dailyInfo.getStartTimeInMinutes());
		tmpList3.add(dailyInfo.getEndTimeInMinutes());
		openHoursList.add(tmpList3);
		
		// convert list to array - openHours
		int[][] openHours = new int[openHoursList.size()][3]; //dim: numOpenHours * 3  //columns: poiIdx(0,1,..), startTime, endTime
		for (int i = 0; i < openHoursList.size(); ++i)
			for (int j = 0; j < 3; ++j) {
				openHours[i][j] = openHoursList.get(i).get(j);
			}
		if (isDisplay) {
			Display.print_2D_Array(openHours, "Display openHours");
		}
		
		// additional data -- declaration
		int[] openHoursCount = new int[numPoi + 2];
		int[] multiOpenHoursConstraintIndex = new int[numPoi + 2];
		int[] bigMHelper_min_a = new int[numPoi + 2]; // min_k {a_ik}
		int[] bigMHelper_max_b = new int[numPoi + 2]; // max_k {b_ik}
	   	
	   	// additional data -- initialization
		for (int i = 0; i < numPoi + 2; ++i) {
	   		multiOpenHoursConstraintIndex[i] = -1;
	   		bigMHelper_min_a[i] = Integer.MAX_VALUE; 
	   		bigMHelper_max_b[i] = Integer.MIN_VALUE; 
	   	}
	   	
		// additional data -- generation
	   	for (int j = 0; j < openHours.length; ++j) {
	   		int node = openHours[j][0];
	   		++openHoursCount[node];
	   		if (openHours[j][1] < bigMHelper_min_a[node])
	   			bigMHelper_min_a[node] = openHours[j][1];
	   		if (openHours[j][2] > bigMHelper_max_b[node])
	   			bigMHelper_max_b[node] = openHours[j][2];
	   	}
	   	if (isDisplay) {
	   		Display.print_1D_Array(bigMHelper_min_a, "Display bigMHelper_min_a");
	   		Display.print_1D_Array(bigMHelper_max_b, "Display bigMHelper_max_b");
	   	}
 	
		int numMultiOpenHoursNodeCount = 0;
		int numMultiOpenHoursBounds = 0;
		int nextIndexOpenHoursConstraint = 0;
		for (int i = 0; i < numPoi + 2; ++i) {
			
			if (openHoursCount[i] > 1) {
				++numMultiOpenHoursNodeCount;
				numMultiOpenHoursBounds += openHoursCount[i];
				multiOpenHoursConstraintIndex[i] = nextIndexOpenHoursConstraint++;
			}
		}
		if (isDisplay) {
			Display.print_1D_Array(openHoursCount, "Display openHoursCount");
			Display.print_1D_Array(multiOpenHoursConstraintIndex, "Display multiOpenHoursConstraintIndex");
		}
		
		//------------------------------------------------
		// index information of constraints and variables
		//------------------------------------------------
		int numNodesInMustCounty = 0;
		int numConstraintInMustCounty = 0;
		String mustCounty = dailyInfo.getMustCounty();
		if (! mustCounty.equals("all")) {
			for (int i = 0; i < poiList.size(); ++i) {
				numNodesInMustCounty += (poiList.get(i).getCountyId().equals( mustCounty ) ? 1 : 0);
			}
			numConstraintInMustCounty = 1;
		}
		int numConstraintNodeArcRelation = numPoi;
		int numConstraintFlowBalance = numPoi + 2;
		int numConstraintTrafficTime = numPoi * (numPoi + 1);
		int numConstraintStayTime = numPoi + 2;
		int numConstraintOpenHoursLB = openHours.length;
		int numConstraintOpenHoursUB = openHours.length;
		int numConstraintMultiOpenHoursSelection = numMultiOpenHoursNodeCount;
		
		int rowbegInMustCounty = 0;
		int rowbegNodeArcRelation = rowbegInMustCounty + numConstraintInMustCounty;
		int rowbegFlowBalance = rowbegNodeArcRelation + numConstraintNodeArcRelation;
		int rowbegTrafficTime = rowbegFlowBalance + numConstraintFlowBalance;
		int rowbegStayTime = rowbegTrafficTime + numConstraintTrafficTime;
		int rowbegOpenHoursLB = rowbegStayTime + numConstraintStayTime;
		int rowbegOpenHoursUB = rowbegOpenHoursLB + numConstraintOpenHoursLB;
		int rowbegMultiOpenHoursSelection = rowbegOpenHoursUB + numConstraintOpenHoursUB;
		int numConstraint = rowbegMultiOpenHoursSelection + numConstraintMultiOpenHoursSelection;
		System.out.println("numConstraint = " + numConstraint);
		
		numVariableW = numPoi;
		numVariableX = numPoi * (numPoi + 1);
		numVariableS = numPoi + 2;
		numVariableT = numPoi + 2;
		numVariableY = numMultiOpenHoursBounds;
		System.out.println("numVariableW=" + numVariableW + ", numVariableX=" + numVariableX + ", numVariableS=" + numVariableS
				+ ", numVariableT=" + numVariableT + ", numVariableY=" + numVariableY);
		
		colbegW = 0;
		colbegX = colbegW + numVariableW;
		colbegS = colbegX + numVariableX;
		colbegT = colbegS + numVariableS;
		colbegY = colbegT + numVariableT;
		numVariable = colbegY + numVariableY;
		//System.out.println("colbegW=" + colbegW + ", colbegX=" + colbegX + ", colbegS=" + colbegS + ", colbegT=" + colbegT
		//					+ ", colbegY=" + colbegY + ", colbegZ=" + colbegZ);
		System.out.println("numVariable = " + numVariable);
		System.out.println("numDiscreteVariable = " + (numVariableW + numVariableX + numVariableY));
		
		// create x-variable subscript mapping, i.e., map (i,j) into an order
		order_to_ij = new int[numPoi * (numPoi + 1)][2]; //columns: node i, node j
		ij_to_order = new int[numPoi + 2][numPoi + 2];
		for (int i = 0; i < ij_to_order.length; ++i)
			Arrays.fill(ij_to_order[i], -1);	
		int k = 0;
		for (int j = 1; j <= numPoi; ++j) {
			order_to_ij[k][0] = 0;
			order_to_ij[k][1] = j;
			ij_to_order[0][j] = k++;
		}
		for (int i = 1; i <= numPoi; ++i)
			for(int j = 1; j <= numPoi + 1; ++j) {
				if (i==j) continue;
				order_to_ij[k][0] = i;
				order_to_ij[k][1] = j;
				ij_to_order[i][j] = k++;
			}
		//System.out.println("----");
		//for (int i = 0; i < order_to_ij.length; ++i)
		//	System.out.println(i + " " + order_to_ij[i][0] + " " + order_to_ij[i][1]);
		//System.out.println("----");
		//for (int i = 0; i < numPoi + 2; ++i) {
		//	for (int j = 0; j < numPoi + 2; ++j) {
		//		System.out.print(ij_to_order[i][j] + " ");
		//	}
		//	System.out.println();
		//}
		//System.out.println("----");
		
		constraintName = new String[numConstraint + 1];
		variableName = new String[numVariable + 1];
		
		//--------------------
		//objective
		//--------------------
		int objDir;
		double objConst = 0;
		double[] objCoef = new double[numVariable];
		
			objDir = LS_MAX;
			for (int i = 0; i < numVariableW; ++i) { //maximize number of visits, emphasize on must nodes
				if (poiList.get(i).isMustPoi())
					objCoef[colbegW + i] = 100;
				else
					objCoef[colbegW + i] = 1;
			}
			
		//--------------------------
		// RHS and constraint types
		//--------------------------
		double[] rhs = new double[numConstraint];
		char[] constraintTypeChar = new char[numConstraint];
		
		// must node
		for (int i = 0; i < numConstraintInMustCounty; ++i) {
			int idx = rowbegInMustCounty + i;
			constraintTypeChar[idx] = 'G';
			rhs[idx] = 1;
			constraintName[idx] = "inMustCounty";
		}
		
		// node arc relation
		for (int i = 0; i < numConstraintNodeArcRelation; ++i) {
			int idx = rowbegNodeArcRelation + i;
			constraintTypeChar[idx] = 'E';
			rhs[idx] = 0;
			constraintName[idx] = "nodeArcRelation_" + i;
		}
		
		// flow balance
		for (int i = 0; i < numConstraintFlowBalance; ++i) {
			int idx = rowbegFlowBalance + i;
			constraintTypeChar[idx] = 'E';
			if (i == 0)
				rhs[idx] = 1;
			else if (i == numConstraintFlowBalance - 1)
				rhs[idx] = -1;
			else
				rhs[idx] = 0;
			constraintName[idx] = "flowBalance_" + i;
		}
		
		// traffic time
		for (int i = 0; i < numConstraintTrafficTime; ++i) {
			int idx = rowbegTrafficTime + i;
			constraintTypeChar[idx] = 'L';
			rhs[idx] = bigMHelper_max_b[ order_to_ij[i][0] ] - bigMHelper_min_a[ order_to_ij[i][1] ];
			constraintName[idx] = "trafficTime_" + i;
		}
		
		// stay time
		for (int i = 0; i < numConstraintStayTime; ++i) {
			int idx = rowbegStayTime + i;
			constraintTypeChar[idx] = 'G';
			rhs[idx] = (i == 0 || i == numPoi + 1) ? 0 : poiList.get(i-1).getStayTime();
			constraintName[idx] = "stayTime_" + i;
		}
		
		// open hours LB
		for (int i = 0; i < numConstraintOpenHoursLB; ++i) {
			int idx = rowbegOpenHoursLB + i;
			constraintTypeChar[idx] = 'L';
			int node = openHours[i][0];
			if (openHoursCount[node] <= 1)
				rhs[idx] = -openHours[i][1]; 
			else
				rhs[idx] = -bigMHelper_min_a[node];
			constraintName[idx] = "openHoursLB_" + i;
		}
		
		// open hours UB
		for (int i = 0; i < numConstraintOpenHoursUB; ++i) {
			int idx = rowbegOpenHoursUB + i;
			constraintTypeChar[idx] = 'L';
			int node = openHours[i][0];
			int stayTime = (node == 0 || node == numPoi + 1) ? 0 : poiList.get(node-1).getStayTime();
			if (openHoursCount[node] <= 1)
				rhs[idx] = openHours[i][2] - stayTime;
			else
				rhs[idx] = bigMHelper_max_b[node] - stayTime;
			constraintName[idx] = "openHoursUB_" + i;
		}
		
		// multiple open hours selection
		for (int i = 0; i < numConstraintMultiOpenHoursSelection; ++i) {
			int idx = rowbegMultiOpenHoursSelection + i;
			constraintTypeChar[idx] = 'E';
			rhs[idx] = 1;
			constraintName[idx] = "multiOpenHoursSelection_" + i;
		}
		
		constraintName[constraintName.length - 1] = null;
		String constraintType = new String(constraintTypeChar);
		
		//---------------------
		//  coefficient of A
		//---------------------
		int numNonzero = numNodesInMustCounty
				+ numConstraintNodeArcRelation * (numPoi + 1)
				+ 2 * numPoi * (numPoi + 1)
				+ numConstraintTrafficTime * 3
				+ numConstraintStayTime * 2
				+ numConstraintOpenHoursLB + numMultiOpenHoursBounds
				+ numConstraintOpenHoursUB + numMultiOpenHoursBounds
				+ numMultiOpenHoursBounds;
		
		System.out.println("numNonzero=" + numNonzero);
		
		int[] begIdxEachCol = new int[numVariable + 1];
		double[] ACoef = new double[numNonzero];
		int[] ACoefRowIdx = new int[numNonzero];
		
		begIdxEachCol[0] = 0;
		k = 0; //overall index
		int tmpCounter;
		
		//------ variable w ------//
		for (int i = 0; i < numVariableW; ++i) { // 0 ~ numPoi-1
			// in must county
			if (numConstraintInMustCounty == 1 && poiList.get(i).getCountyId().equals( mustCounty )) {
				ACoef[k] = 1;
				ACoefRowIdx[k++] = rowbegInMustCounty;
			}
			// node arc relation
			ACoef[k] = 1;
			ACoefRowIdx[k++] = rowbegNodeArcRelation + i;
			
			begIdxEachCol[colbegW + i + 1] = begIdxEachCol[colbegW + i] +
							((numConstraintInMustCounty == 1 && poiList.get(i).getCountyId().equals( mustCounty )) ? 1 : 0) + 1;
			variableName[colbegW + i] = "w_" + String.valueOf(i + 1);
		}
		
		//------ variable x ------//
		for (int i = 0; i < numVariableX; ++i) {
			int arcStartNode = order_to_ij[i][0];
			int arcEndNode = order_to_ij[i][1];
			tmpCounter = 0;
			
			// node arc relation
			if (arcStartNode >= 1 && arcStartNode <= numPoi) {
				ACoef[k] = -1;
				ACoefRowIdx[k++] = rowbegNodeArcRelation + (arcStartNode - 1);
				++tmpCounter;
			}
			
			// flow balance
			if (arcStartNode <= arcEndNode) {
			ACoef[k] = 1;
			ACoefRowIdx[k++] = rowbegFlowBalance + arcStartNode;
			ACoef[k] = -1;
			ACoefRowIdx[k++] = rowbegFlowBalance + arcEndNode;
			}
			else {
			ACoef[k] = -1;
			ACoefRowIdx[k++] = rowbegFlowBalance + arcEndNode;	
			ACoef[k] = 1;
			ACoefRowIdx[k++] = rowbegFlowBalance + arcStartNode;		
			}
			tmpCounter += 2;
			
		// traffic time
			ACoef[k] = travelTime[arcStartNode][arcEndNode] + bigMHelper_max_b[arcStartNode] - bigMHelper_min_a[arcEndNode];
			ACoefRowIdx[k++] = rowbegTrafficTime + i;
			++tmpCounter;
			
			begIdxEachCol[colbegX + i + 1] = begIdxEachCol[colbegX + i] + tmpCounter;
			variableName[colbegX + i] = "x_" + String.valueOf(arcStartNode) + "_" + String.valueOf(arcEndNode);
		}
		
		//------ variable s ------//
		for (int i = 0; i < numVariableS; ++i) { // 0 ~ numPoi + 1  // i means arcEndNode
			tmpCounter = 0;
			
			// traffic time
			for (int arcStartNode = 0; arcStartNode < numPoi + 2; ++arcStartNode) {
				int order = ij_to_order[arcStartNode][i]; 
				if ( order >= 0 ) {
					ACoef[k] = -1;
					ACoefRowIdx[k++] = rowbegTrafficTime + order;
					++tmpCounter;
				}
			}
			
			// stay time
			ACoef[k] = -1;
			ACoefRowIdx[k++] = rowbegStayTime + i;
			++tmpCounter;
			
			// open hours LB
			for (int j = 0; j < openHours.length; ++j) {
				if (openHours[j][0] == i) {
					ACoef[k] = -1;
					ACoefRowIdx[k++] = rowbegOpenHoursLB + j;
					++tmpCounter;
				}
			}
			
			// open hours UB
			for (int j = 0; j < openHours.length; ++j) {
				if (openHours[j][0] == i) {
					ACoef[k] = 1;
					ACoefRowIdx[k++] = rowbegOpenHoursUB + j;
					++tmpCounter;
				}
			}
			
			begIdxEachCol[colbegS + i + 1] = begIdxEachCol[colbegS + i] + tmpCounter;
			variableName[colbegS + i] = "s_" + String.valueOf(i);
		}
		
		//------ variable t ------//
		for (int i = 0; i < numVariableT; ++i) { // 0 ~ numPoi + 1  // i means arcStartNode
			tmpCounter = 0;
			
			// traffic time
			for (int arcEndNode = 0; arcEndNode < numPoi + 2; ++arcEndNode) {
				int order = ij_to_order[i][arcEndNode];
				if (order >= 0) {
					ACoef[k] = 1;
					ACoefRowIdx[k++] = rowbegTrafficTime + order;
					++tmpCounter;
				}
			}
			
			// stay time
			ACoef[k] = 1;
			ACoefRowIdx[k++] = rowbegStayTime + i;
			++tmpCounter;
			
			begIdxEachCol[colbegT + i + 1] = begIdxEachCol[colbegT + i] + tmpCounter;
			variableName[colbegT + i] = "t_" + String.valueOf(i);
		}
		
		//------ variable y ------//
		int y = 0;
			for (int i = 0; i < openHours.length; ++i) {
				int node = openHours[i][0];
				if (openHoursCount[node] > 1) {
					ACoef[k] = openHours[i][1] - bigMHelper_min_a[node];
					ACoefRowIdx[k++] = rowbegOpenHoursLB + i;
					
					ACoef[k] = bigMHelper_max_b[node] - openHours[i][2];
					ACoefRowIdx[k++] = rowbegOpenHoursUB + i;
					
					ACoef[k] = 1;
					ACoefRowIdx[k++] = rowbegMultiOpenHoursSelection + multiOpenHoursConstraintIndex[node];
					
					begIdxEachCol[colbegY + y + 1] = begIdxEachCol[colbegY + y] + 3;
					variableName[colbegY + y] = "y_" + String.valueOf(i);
					++y;
				}
			}
			
			
			variableName[variableName.length - 1] = null;
		
		//---------------------
		//variable type
		//---------------------
		double[] varLowerBound = null;
		double[] varUpperBound = null;
		char[] variableTypeChar = new char[numVariable];
		for (int i = 0; i < numVariableW; ++i)
			variableTypeChar[colbegW + i] = 'B';
		for (int i = 0; i < numVariableX; ++i)
			variableTypeChar[colbegX + i] = 'B';
		for (int i = 0; i < numVariableS; ++i)
			variableTypeChar[colbegS + i] = 'C';
		for (int i = 0; i < numVariableT; ++i)
			variableTypeChar[colbegT + i] = 'C';
		for (int i = 0; i < numVariableY; ++i)
			variableTypeChar[colbegY + i] = 'B';
		String variableType = new String(variableTypeChar);

		//--------------------
		//   model
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
			nErrorCode[0] = LSwriteLINDOFile(pModel, "C:/Users/wu/Downloads/log_LINDO_2/mip_r1_" + dateLindoStart.getTime() + ".ltx");
		
		//--------------------
		//  solving
		//--------------------
		System.out.println("LINDO model 2, run1, start solving...");
			
		int[] nStatus = new int[1];
		nErrorCode[0] = LSsolveMIP(pModel, nStatus);
		APIErrorCheck(pEnv);
		
		objValue = new double[1];
		solution = new double[numVariable];
		LSgetInfo(pModel, LS_DINFO_MIP_OBJ, objValue);
		LSgetMIPPrimalSolution(pModel, solution);
		System.out.println("nStatus=" + nStatus[0]); //1:optimal 3:infeasible (from testing)
		
		
		Date dateLindoEnd = new Date(System.currentTimeMillis());
		System.out.print("LINDO model 2, run1, elapsed:");
		System.out.println(dateLindoEnd.getTime() - dateLindoStart.getTime());
		
		return nStatus[0];
	}
	//--------------------------------------------
	//  setting up MIP model and solving the MIP
	//--------------------------------------------
	public int run2() {
		Date dateLindoStart = new Date(System.currentTimeMillis());
		
		// constructing added constraints
//		for (int i = 0; i < numVariableW; ++i)
//			if (solution[colbegW + i] > oneBelow )
		
		int numAddedConstraint = numVariableW;
		int numAddedNonzero = numVariableW;
		char[] addedConstraintTypeChar = new char[numAddedConstraint];
		String[] addedConstraintName = new String[numAddedConstraint + 1];
		
		int[] begIdxEachAddedRow = new int[numAddedConstraint + 1];
		double[] addedACoef = new double[numAddedNonzero];
		int[] addedACoefColumnIdx = new int[numAddedNonzero];
		double[] addedRhs = new double[numAddedConstraint];
		
		for (int i = 0; i < numAddedConstraint; ++i) {
			addedConstraintTypeChar[i] = 'E';
			addedConstraintName[i] = "nodeSelection_" + i;
			begIdxEachAddedRow[i] = i;
			addedACoef[i] = 1;
			addedACoefColumnIdx[i] = colbegW + i;
			addedRhs[i] = (solution[colbegW + i] > oneBelow ? 1 : 0);
		}
		String addedConstraintType = new String(addedConstraintTypeChar);
		addedConstraintName[addedConstraintName.length - 1] = null;
		begIdxEachAddedRow[numAddedConstraint] = numAddedConstraint;
		
		LSaddConstraints( pModel, numAddedConstraint, addedConstraintType, addedConstraintName, begIdxEachAddedRow, addedACoef, addedACoefColumnIdx, addedRhs); //p264
		
		// constructing new objective
		int numModifiedObjCoef = numVariableW + numVariableX;
		int[] modifiedObjColumnIdx = new int[numModifiedObjCoef];
		double[] modifiedObjCoef = new double[numModifiedObjCoef];
		
			for (int i = 0; i < numVariableW; ++i) {
				modifiedObjColumnIdx[i] = colbegW + i;
				modifiedObjCoef[i] = 0;
			}
			for (int i = 0; i < numVariableX; ++i) {
				modifiedObjColumnIdx[numVariableW + i] = colbegX + i;
				modifiedObjCoef[numVariableW + i] = - travelTime[ order_to_ij[i][0] ][ order_to_ij[i][1] ];
			}
		
		LSmodifyObjective( pModel, numModifiedObjCoef,modifiedObjColumnIdx, modifiedObjCoef); //p279
		
		if (System.getProperty("os.name").equals("Windows 7"))
			nErrorCode[0] = LSwriteLINDOFile(pModel, "C:/Users/wu/Downloads/log_LINDO_2/mip_r2_" + dateLindoStart.getTime() + ".ltx");

		//--------------------
		//  solving
		//--------------------
		System.out.println("LINDO model 2, run2, start solving...");
			
		int[] nStatus = new int[1];
		nErrorCode[0] = LSsolveMIP(pModel, nStatus);
		APIErrorCheck(pEnv);
		
		LSgetInfo(pModel, LS_DINFO_MIP_OBJ, objValue);
		LSgetMIPPrimalSolution(pModel, solution);
		System.out.println("nStatus=" + nStatus[0]); //1:optimal 3:infeasible (from testing)
		
		
			Date dateLindoEnd = new Date(System.currentTimeMillis());
			System.out.print("LINDO model 2, run2, elapsed:");
			System.out.println(dateLindoEnd.getTime() - dateLindoStart.getTime());
			
			return nStatus[0];
		}
		//------------------------
		//   examining solution
		//------------------------
		public void examineSolution() {
		double zeroAbove = 0.001;
		
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
		
		counter = 0;
		for (int i = 0; i < numVariableS; ++i)  
			if (solution[colbegS + i] > zeroAbove )
				++counter;
		System.out.println("Number of (s>0): " + counter);
		
		counter = 0;
		for (int i = 0; i < numVariableT; ++i)  
			if (solution[colbegT + i] > zeroAbove )
				++counter;
		System.out.println("Number of (t>0): " + counter);
		
		counter = 0;
		for (int i = 0; i < numVariableY; ++i)  
			if (solution[colbegY + i] > oneBelow )
				++counter;
		System.out.println("Number of (y==1): " + counter);
	}

	//------------------------
	//   extracting solution
	//------------------------
	public List<TourEvent> extractSolution()
	{
		List<TourEvent> itinerary = new ArrayList<TourEvent>();
		itineraryPoi = new ArrayList<Poi>();
		
			int arcStartNode = 0;
			int counter = 0;
				System.out.println(arcStartNode + " " + solution[colbegS + arcStartNode] + " " + solution[colbegT + arcStartNode]);
			while (true) {
				boolean isFound = false;
				for (int i = 1; i < numPoi + 2; ++i) {
				if (i != arcStartNode && solution[ colbegX + ij_to_order[arcStartNode][i] ] > oneBelow) {
					System.out.println(i + " " + solution[colbegS + i] + " " + solution[colbegT + i]);
					
					if (i != numPoi + 1) {
						TourEvent tourEvent = new TourEvent();
						tourEvent.setPoiId(dailyInfo.getPoiList().get(i - 1).getPoiId());
						
						Calendar calendar = dailyInfo.getCalendarForDay();
						calendar.set(Calendar.HOUR_OF_DAY, (int)solution[colbegS + i] / 60);
						calendar.set(Calendar.MINUTE, (int)solution[colbegS + i] % 60);
						tourEvent.setStartTime(calendar.getTime());
						calendar.set(Calendar.HOUR_OF_DAY, (int)solution[colbegT + i] / 60);
						calendar.set(Calendar.MINUTE, (int)solution[colbegT + i] % 60);
						tourEvent.setEndTime(calendar.getTime());
						itinerary.add(tourEvent);
						
						itineraryPoi.add(dailyInfo.getPoiList().get(i - 1));
					}
					
					isFound = true;
					arcStartNode = i;
					break;
				}
				}
				if (arcStartNode == numPoi + 1)
					break;
				
				if (!isFound) {
					System.err.println("Lindo solution problem! x-variable not connected!");
					break;
				}
				if (++counter > numPoi + 1) {
					System.err.println("Lindo solution problem! x-variable cycling!");
					break;
				}
			}
		return itinerary;
	}
	public List<Poi> extractSolutionPoi() {
		return itineraryPoi;
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
