package tw.org.iii.st.analytics.controller;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import com.lindo.*;

public class TestLindo2 extends Lindo
{
    private static int nErrorCode[] = new int[1];
    private static StringBuffer cErrorMessage = new StringBuffer(LS_MAX_ERROR_MESSAGE_LENGTH);
    private static StringBuffer cLicenseKey = new StringBuffer(LS_MAX_ERROR_MESSAGE_LENGTH);
    double[][] array = null;
    double[] adC = null;
    double[] adB = null;

    private static Object pEnv = null;
    private static Object pModel = null;

    private static Object[] a = null;
    /* Number of constraints, Number of variables  */
    static int nM = 0, nN = 0;
    /* The length of each column.  Since we aren't leaving any blanks in our matrix, we can set this to null */
    static int pnLenCol[] = null;


    /* The nonzero coefficients */
    static double adA[] = null;
    /* The row indices of the nonzero coefficients */
    static int anRowX[] = null;

    /* Simple upper and lower bounds on the variables.
         By default, all variables have a lower bound of zero
         and an upper bound of infinity.  Therefore pass null
         pointers in order to use these default values. */
    static double pdLower[] = null, pdUpper[] = null;

    /* The number of nonzeros in the constraint matrix */static int nNZ = 0;
    /* The indices of the first nonzero in each column */static int anBegCol[] = null;
    /* The length of each column. Since there are no balnks can be null */static int[]
        Alencol = null;
    /* LB and UB of the variable, null means 0 LB and infinity UB */static double[]
        lb = null, ub = null;

    static String varnames[] = null, connames[] = null;

    static {
        // The runtime system executes a class's static
        // initializer when it loads the class.

        nErrorCode[0] = LSloadLicenseString("C:/Lindoapi/license/lndapi90-trial-hyper.lic", cLicenseKey);
        APIErrorCheck(pEnv);

        pEnv = LScreateEnv(nErrorCode, cLicenseKey.toString());
        APIErrorCheck(pEnv);
        
        System.out.println("!");
        System.out.println(cLicenseKey.toString());
        System.out.println("!");

        pModel = LScreateModel(pEnv, nErrorCode);
      }

    // Generalized error Reporting function
    private static void APIErrorCheck(Object pEnv) {
      if (0 != nErrorCode[0]) {
        LSgetErrorMessage(pEnv, nErrorCode[0], cErrorMessage);
        System.out.println("\nError " + nErrorCode[0] + ": " + cErrorMessage);
        System.out.println();
        System.exit(1);
      }
    }

    // Version Reporting function
    private static void APIVERSION()
    {
         StringBuffer szVersion = new StringBuffer(255);
         StringBuffer szBuild   = new StringBuffer(255);
         LSgetVersionInfo(szVersion, szBuild);
         System.out.println("\nLINDO API Version "+szVersion.toString() + " built on " + szBuild.toString());
         System.out.println();
    }

    private static double[][] getMatrixFromFile(String inFilename) {
    	BufferedReader br = null;
    	String line = "";
    	String cvsSplitBy = ",";
    	List<String> collectedLines = new ArrayList<String>();
    	try {
    		br = new BufferedReader(new FileReader(inFilename));
    		while ((line = br.readLine()) != null)
    			collectedLines.add(line);
    	} catch (FileNotFoundException e) {
    		e.printStackTrace();
    	} catch (IOException e) {
    		e.printStackTrace();
    	} finally {
    		if (br != null) {
    			try {
    				br.close();
    			} catch (IOException e) {
    				e.printStackTrace();
    			}
    		}
    	}
    	
    	double data[][] = new double[collectedLines.size()][];
	    for (int i=0; i<collectedLines.size(); ++i) {
			String[] splitData = collectedLines.get(i).split(cvsSplitBy);
			double temp[] = new double[splitData.length];
			for (int j=0; j<splitData.length; ++j) {
				temp[j] = Double.parseDouble(splitData[j]);
				//System.out.print(temp[j]);
				//System.out.print("\t");
				data[i] = temp;
			}
			//System.out.print("\n");
	    }
	    return data;
    }
    private static void displayMatrix(double[][] data) {
    	for(int i=0; i<data.length; ++i) {
    		for(int j=0; j<data[i].length; ++j) {
    			System.out.print(data[i][j]);
    			System.out.print("\t");
    		}
    		System.out.print("\n");
    	}
    	System.out.print("\n");
    }

    public static void run() {
    	Date date1 = new Date(System.currentTimeMillis());
    	
    	//--------------------
    	//       data
    	//--------------------
    	int numPOI = 5;
    	
    	// read example distance, dim = [numPOI+2][numPOI+2]
    	double travelTime[][] = getMatrixFromFile("C:/data/computer/LINDO/example/exampleDistance.txt"); //[#POI+2][#POI+2]
    	//displayMatrix(travelTime);
    	
    	// read example stay hours, dim = [numPOI+2][1]
       	double stayTime[][] = getMatrixFromFile("C:/data/computer/LINDO/example/exampleStayTime.txt");
       	//displayMatrix(stayTime);
    	
       	// construct time interval
       	double timeInterval[][] = new double[numPOI+2][numPOI+2];
       	for (int i=0; i<numPOI+2; ++i)
       		for (int j=0; j<numPOI+2; ++j)
       			timeInterval[i][j] = stayTime[i][0] + travelTime[i][j];
       	//displayMatrix(timeInterval);
       	
    	
    	// read example open hours, dim = [][3]
       	double openHours[][] = getMatrixFromFile("C:/data/computer/LINDO/example/exampleopenHours.txt");
       	//displayMatrix(openHours);
    	
    	// construct openHoursSummary
    	double openHoursSummary[][] = new double[numPOI+2][4];
    	for (int i=0; i<numPOI+2; ++i) {
        	openHoursSummary[i][0] = 0; // count of open hours
        	openHoursSummary[i][1] = Double.POSITIVE_INFINITY; // min a (min LB)
        	openHoursSummary[i][2] = Double.NEGATIVE_INFINITY; // max b (max UB)
        	openHoursSummary[i][3] = -1; // index of POI with count >= 2
    	}

    	for (int j=0; j<openHours.length; ++j) {
    		int idx = (int)openHours[j][0];
    		++openHoursSummary[idx][0];
    		if (openHours[j][1] < openHoursSummary[idx][1])
    			openHoursSummary[idx][1] = openHours[j][1];
    		if (openHours[j][2] > openHoursSummary[idx][2])
    			openHoursSummary[idx][2] = openHours[j][2];	
    	}
       	    	
    	int numMultiopenHoursNodeCount = 0;
    	int numMultiopenHoursBounds = 0;
    	for (int i=1; i<=numPOI; ++i) {
    		openHoursSummary[i][2] = openHoursSummary[i][2] - stayTime[i][0]; // adjust max b!!
    		
    		if (openHoursSummary[i][0] > 1) {
    			++numMultiopenHoursNodeCount;
    			numMultiopenHoursBounds += openHoursSummary[i][0]; //col0=count

    			openHoursSummary[i][3] = openHoursSummary[i-1][3] + 1;
    		}
    		else
    			openHoursSummary[i][3] = openHoursSummary[i-1][3];
    	}
    	//displayMatrix(openHoursSummary);
    	
       	
    	//------------------------------------------------
    	// index information of constraints and variables
    	//------------------------------------------------
       	int numConstraintOutflow = numPOI + 1;
       	int numConstraintInflow = numPOI + 1;
       	int numConstraintStartTime = numPOI * (numPOI + 1);
       	int numConstraintopenHoursLB = openHours.length;
       	int numConstraintopenHoursUB = openHours.length;
       	int numConstraintMultiopenHours = numMultiopenHoursNodeCount;
       	
       	int rowbegOutflow = 0;
       	int rowbegInflow = rowbegOutflow + numConstraintOutflow;
       	int rowbegStartTime = rowbegInflow + numConstraintInflow;
       	int rowbegopenHoursLB = rowbegStartTime + numConstraintStartTime;
       	int rowbegopenHoursUB = rowbegopenHoursLB + numConstraintopenHoursLB;
       	int rowbegMultiopenHours = rowbegopenHoursUB + numConstraintopenHoursUB;
       	int numConstraint = rowbegMultiopenHours + numConstraintMultiopenHours;
       	
       	int numVariableX = numPOI * (numPOI + 1);
       	int numVariableS = numPOI + 2;
       	int numVariableY = numMultiopenHoursBounds;
       	
       	int colbegX = 0;
       	int colbegS = colbegX + numVariableX;
       	int colbegY = colbegS + numVariableS;
    	int numVariable = colbegY + numVariableY;
    	
    	// create x-variable subscript
    	int xSubscriptMap[][]  = new int[numPOI * (numPOI+1)][2];
    	int k = 0;
    	for (int j=1; j<=numPOI; ++j) {
    		xSubscriptMap[k][0] = 0;
    		xSubscriptMap[k++][1] = j;
    	}
    	for (int i=1; i<=numPOI; ++i)
    		for(int j=1; j<=numPOI+1; ++j) {
    			if (i==j) continue;
    			xSubscriptMap[k][0] = i;
    			xSubscriptMap[k++][1] = j;
    		}
    	// display x-variable subscript
    	/*k = 0;
    	for (int i=0; i<xSubscriptMap.length; ++i) {
    		System.out.print(k++);
    		System.out.print(" ");
    		System.out.print(xSubscriptMap[i][0]);
    		System.out.print(" ");
    		System.out.println(xSubscriptMap[i][1]);
    	}*/
    	String constraintName[] = new String[numConstraint + 1];
    	String variableName[] = new String[numVariable + 1];
    	
    	//--------------------
    	//    objective
    	//--------------------
    	int objDir = LS_MIN;
    	double objConst = 0;
    	double objCoef[] = new double[numVariable];
    	for (int i=colbegX; i < colbegX + numVariableX; ++i)
    		objCoef[i] = travelTime[ xSubscriptMap[i][0] ][ xSubscriptMap[i][1] ];
    	objCoef[colbegY-1] = 0.001; // coef of s_{N+1}
    	
    	//--------------------------
    	// RHS and constraint types
    	//--------------------------
    	double rhs[] = new double[numConstraint];
    	char constraintTypeChar[] = new char[numConstraint];
    	for (int i=0; i<numConstraintOutflow; ++i) {
    		constraintTypeChar[i + rowbegOutflow] = 'E';
       		rhs[i + rowbegOutflow] = 1;
       		constraintName[i + rowbegOutflow] = "outflow_" + String.valueOf(i);
    	}
    	for (int i=0; i<numConstraintInflow; ++i) {
    		constraintTypeChar[i + rowbegInflow] = 'E';
    		rhs[i + rowbegInflow] = 1;
    		constraintName[i + rowbegInflow] = "inflow_" + String.valueOf(i);
    	}
    	for (int i=0; i<numConstraintStartTime; ++i) {
    		constraintTypeChar[i + rowbegStartTime] = 'L';
    		rhs[i + rowbegStartTime] = openHoursSummary[ xSubscriptMap[i][0] ][2] //col2=maxb
    									- openHoursSummary[ xSubscriptMap[i][1] ][1]; //col1=mina
    		constraintName[i + rowbegStartTime] = "startTime_" + String.valueOf(i);
    	}
    	for (int i=0; i<numConstraintopenHoursLB; ++i) {
    		constraintTypeChar[i + rowbegopenHoursLB] = 'L';
    		if (openHoursSummary[(int)openHours[i][0]][0] > 1)
    			rhs[i + rowbegopenHoursLB] = -openHoursSummary[ (int)openHours[i][0] ][1]; //col1=mina
    		else
    			rhs[i + rowbegopenHoursLB] = -openHours[i][1];
    		constraintName[i + rowbegopenHoursLB] = "openHoursLB_" + String.valueOf(i);
    	}
    	for (int i=0; i<numConstraintopenHoursUB; ++i) {
    		constraintTypeChar[i + rowbegopenHoursUB] = 'L';
    		if (openHoursSummary[(int)openHours[i][0]][0] > 1)
    			rhs[i + rowbegopenHoursUB] = openHoursSummary[ (int)openHours[i][0] ][2]; //col2=maxb
    		else
    			rhs[i + rowbegopenHoursUB] = openHours[i][2] - stayTime[ (int)openHours[i][0] ][0];
    		constraintName[i + rowbegopenHoursUB] = "openHoursUB_" + String.valueOf(i);
    	}
    	for (int i=0; i<numConstraintMultiopenHours; ++i) {
    		constraintTypeChar[i + rowbegMultiopenHours] = 'E';
    		rhs[i + rowbegMultiopenHours] = 1;
    		constraintName[i + rowbegMultiopenHours] = "multiopenHours_" + String.valueOf(i);
    	}
    	constraintName[constraintName.length - 1] = null;
    	String constraintType = new String(constraintTypeChar);
    	
    	//--------------------
    	// coefficient of A
    	//--------------------
    	int numNonzero = numConstraintOutflow * numPOI
    					+ numConstraintInflow * numPOI
    					+ numConstraintStartTime * 3
    					+ numConstraintopenHoursLB + numMultiopenHoursBounds
    					+ numConstraintopenHoursUB + numMultiopenHoursBounds
    					+ numMultiopenHoursBounds;
    	//System.out.println(numNonzero); //#POI=4 (2,2,2,2)example: 140
    	
    	int begIdxEachCol[] = new int[numVariable + 1];
    	double ACoef[] = new double[numNonzero];
    	int ACoefRowIdx[] = new int[numNonzero];
    	
    	//-- variable x --//
    	begIdxEachCol[0] = 0;
    	k = 0;
    	for (int i=0; i<numVariableX; ++i) {
    		int outNode = xSubscriptMap[i][0];
    		int inNode = xSubscriptMap[i][1];
    		
    		ACoef[k] = 1;
    		ACoefRowIdx[k++] = rowbegOutflow + outNode;
    		
    		ACoef[k] = 1;
    		ACoefRowIdx[k++] = rowbegInflow + (inNode - 1);
    		
    		ACoef[k] = timeInterval[outNode][inNode]
    					+ openHoursSummary[outNode][2] - openHoursSummary[inNode][1];
    		ACoefRowIdx[k++] = rowbegStartTime + i;
    		
    		begIdxEachCol[colbegX+i+1] = begIdxEachCol[colbegX+i] + 3;
    		variableName[colbegX+i] = "x_" + String.valueOf(outNode) + "_" + String.valueOf(inNode);
    	}
    	
    	//-- variable s --//
    	for (int j=0; j<numVariableS; ++j) {
    		int cnt = 0;
    		for (int i=0; i<xSubscriptMap.length; ++i) {
    			if (xSubscriptMap[i][0] == j) {
    				ACoef[k] = 1;
    				ACoefRowIdx[k++] = rowbegStartTime + i;
    				++cnt;
    			}
    			if (xSubscriptMap[i][1] == j) {
    				ACoef[k] = -1;
    				ACoefRowIdx[k++] = rowbegStartTime + i;
    				++cnt;
    			}
    		}
    		for (int i=0; i<openHours.length; ++i) {
    			if (openHours[i][0] == j) {
    				ACoef[k] = -1;
    				ACoefRowIdx[k++] = rowbegopenHoursLB + i;
    				++cnt;
    			}
    		}
    		for (int i=0; i<openHours.length; ++i) {
    			if (openHours[i][0] == j) {
    				ACoef[k] = 1;
    				ACoefRowIdx[k++] = rowbegopenHoursUB + i;
    				++cnt;
    			}
    		}
    		begIdxEachCol[colbegS+j+1] = begIdxEachCol[colbegS+j] + cnt;
    		variableName[colbegS+j] = "s_" + String.valueOf(j);
    	}
    	
    	//-- variable y --//
    	int y = 0;
		for (int i=0; i<openHours.length; ++i) {
			int node = (int)openHours[i][0];
			if (openHoursSummary[node][0] > 1) {
				ACoef[k] = openHours[i][1] - openHoursSummary[node][1];
				ACoefRowIdx[k++] = rowbegopenHoursLB + i;
				
				ACoef[k] = openHoursSummary[node][2] - openHours[i][2];
				ACoefRowIdx[k++] = rowbegopenHoursUB + i;
				
				ACoef[k] = 1;
				ACoefRowIdx[k++] = rowbegMultiopenHours + (int)openHoursSummary[node][3];
				
				begIdxEachCol[colbegY+y+1] = begIdxEachCol[colbegY+y] + 3;
				variableName[colbegY+y] = "y_" + String.valueOf(i);
				++y;
			}
		}
		variableName[variableName.length - 1] = null;
		//System.out.println(numVariable);
		//System.out.println(numConstraint);
		//for (int i=0; i<begIdxEachCol.length; ++i)
		//	System.out.println(begIdxEachCol[i]);

    	//--------------------
    	//    variables
    	//--------------------
    	double varLowerBound[] = null;
    	double varUpperBound[] = null;
    	char variableTypeChar[] = new char[numVariable];
    	for (int i=0; i<numVariableX; ++i)
    		variableTypeChar[i + colbegX] = 'B';
    	for (int i=0; i<numVariableS; ++i)
    		variableTypeChar[i + colbegS] = 'C';
    	for (int i=0; i<numVariableY; ++i)
    		variableTypeChar[i + colbegY] = 'B';
    	String variableType = new String(variableTypeChar);

    	//--------------------
    	//       model
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
        
        nErrorCode[0] = LSwriteLINDOFile(pModel, "C:/Users/wu/Downloads/f1.ltx");
    	
    	//--------------------
    	//      solve
    	//--------------------
        int nStatus[] = new int[1];
        nErrorCode[0] = LSsolveMIP(pModel, nStatus);
        APIErrorCheck(pEnv);
        
		double objValue[] = new double[1];
		double solution[] = new double[numVariable];
        LSgetInfo(pModel, LS_DINFO_MIP_OBJ, objValue);
        LSgetMIPPrimalSolution(pModel, solution);
        
        System.out.println(objValue[0]);
        for (int i=0; i<solution.length; ++i) {
        	System.out.print(variableName[i] + " ");
        	System.out.println(solution[i]);
        }
        
        nErrorCode[0] = LSdeleteEnv(pEnv);
        
    	Date date2 = new Date(System.currentTimeMillis());
    	System.out.println(date2.getTime() - date1.getTime());
    	System.out.println(numVariable);
    	System.out.println(numConstraint);
    }

}

