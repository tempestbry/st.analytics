package tw.org.iii.st.analytics.controller;

import com.lindo.*;

public class TestLindo extends Lindo
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
    /* Number of constraints, Number of variables  */static int nM = 0, nN = 0;
    /* The length of each column.  Since we aren't leaving any blanks in our matrix, we can set this to null */
    static int pnLenCol[] = null;

    /* The nonzero coefficients */static double adA[] = null;

    /* The row indices of the nonzero coefficients */static int anRowX[] = null;

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

//      System.loadLibrary("lindojni");
      nErrorCode[0] = LSloadLicenseString("C:/Lindoapi/license/lndapi90.lic", cLicenseKey);
      APIErrorCheck(pEnv);

      pEnv = LScreateEnv(nErrorCode, cLicenseKey.toString());
      APIErrorCheck(pEnv);

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


    public void run()
    {

    /* Number of constraints */
     int nM = 3;

    /* Number of variables */
     int nN = 2;

     /* The direction of optimization */
        int nDir = LS_MAX;

    /* The objective's constant term */
        double dObjConst = 0.;

    /* The coefficients of the objective function */
        double adC[] = new double[] { 20., 30.};

    /* The right-hand sides of the constraints */
        double adB[] = new double[] { 120., 60., 50.};

    /* The constraint types */
        String acConTypes = "LLL";

    /* The number of nonzeros in the constraint matrix */
        int nNZ = 4;

    /* The indices of the first nonzero in each column */
        int anBegCol[] = new int[] { 0, 2, nNZ};

    /* The length of each column.  Since we aren't leaving
      any blanks in our matrix, we can set this to null */
        int pnLenCol[] = null;

    /* The nonzero coefficients */
        double adA[] = new double[]{ 1., 1., 2., 1.};

    /* The row indices of the nonzero coefficients */
        int anRowX[] = new int[] { 0, 1, 0, 2};

    /* Simple upper and lower bounds on the variables.
      By default, all variables have a lower bound of zero
      and an upper bound of infinity.  Therefore pass null
      pointers in order to use these default values. */
        double pdLower[] = null, pdUpper[] = null;

     APIVERSION();

    /* We have now assembled a full description of the model.
      We pass this information to LSloadLPData with the
      following call. */
        nErrorCode[0] = LSloadLPData(pModel, nM, nN, nDir,
         dObjConst, adC, adB, acConTypes, nNZ, anBegCol,
         pnLenCol, adA, anRowX, pdLower, pdUpper);
        APIErrorCheck(pEnv);

       String varnames[] = {"Variable1","Variable2"};
       String connames[] = {"Constraint1","Constraint2","Constraint3"};

       nErrorCode[0] = LSloadNameData(pModel, "MyTitle","MyObj",null,null,
          null,connames,varnames,null);
       APIErrorCheck(pEnv);

       nErrorCode[0] = LSwriteLINDOFile(pModel,"C:/Users/wu/Downloads/temp.ltx");
       APIErrorCheck(pEnv);

       if (true) {
      /* >>> Step 6 <<< Retrieve the solution */
          int i;
          double adX[] = new double[100], dObj[]= new double[1];
          double adDec[] = new double[100], adInc[]= new double[100];

      /* >>> Step 5 <<< Perform the optimization */
         nErrorCode[0] = LSoptimize( pModel, LS_METHOD_PSIMPLEX, null);
         APIErrorCheck(pEnv);

      /* Get the value of the objective */
          nErrorCode[0] = LSgetInfo(pModel,LS_DINFO_POBJ, dObj);
         APIErrorCheck(pEnv);

          System.out.println( "Objective Value =\n"+dObj[0]);

      /* Get the variable values */
          nErrorCode[0] = LSgetPrimalSolution ( pModel, adX);
          APIErrorCheck(pEnv);

          int[] nVar = new int[1];
          nErrorCode[0] = LSgetInfo ( pModel, LS_IINFO_NUM_VARS, nVar);
          System.out.println("Primal values = ");
          for (i = 0; i < nN; i++) System.out.println( "x["+i+"]="+adX[i]);
          System.out.println("\n");

          /* >>> Step 7 <<< Delete the LINDO environment */
          nErrorCode[0] = LSdeleteEnv( pEnv);

       }
    }
}

