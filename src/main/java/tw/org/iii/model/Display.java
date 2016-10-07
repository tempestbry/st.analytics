package tw.org.iii.model;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class Display<E> {
	public static void print_1D_Array(boolean[] a) {
		for (int i = 0; i < a.length; ++i)
			System.out.println(a[i]);
	}
	public static void print_1D_Array(int[] a) {
		for (int i = 0; i < a.length; ++i)
			System.out.println(a[i]);
	}
	public static void print_1D_Array(double[] a) {
		for (int i = 0; i < a.length; ++i)
			System.out.println(a[i]);
	}
	public static <E> void print_1D_Array(E[] a) {
		for (int i = 0; i < a.length; ++i)
			System.out.println(a[i]);
	}
	
	public static void print_2D_Array(boolean[][] a) {
		for (int i = 0; i < a.length; ++i) {
			for (int j = 0; j < a[i].length; ++j)
				System.out.print((a[i][j] ? 1 : 0) + " ");
			System.out.println();
		}
	}
	public static void print_2D_Array(int[][] a) {
		for (int i = 0; i < a.length; ++i) {
			for (int j = 0; j < a[i].length; ++j)
				System.out.print(a[i][j] + " ");
			System.out.println();
		}
	}
	public static void print_2D_Array(double[][] a) {
		for (int i = 0; i < a.length; ++i) {
			for (int j = 0; j < a[i].length; ++j)
				System.out.print(a[i][j] + " ");
			System.out.println();
		}
	}	
	public static <E> void print_2D_Array(E[][] a) {
		for (int i = 0; i < a.length; ++i) {
			for (int j = 0; j < a[i].length; ++j)
				System.out.print(a[i][j] + " ");
			System.out.println();
		}
	}

	public static <E> void print_1D_List(List<E> a) {
		for (int i = 0; i < a.size(); ++i)
			System.out.println(a.get(i));
	}
	public static <E> void print_2D_List(List<List<E>> a) {
		for (int i = 0; i < a.size(); ++i) {
			for (int j = 0; j < a.get(i).size(); ++j)
				System.out.print(a.get(i).get(j) + " ");
			System.out.println();
		}
	}


	public static void print_1D_Array(boolean[] a, String notes) {
		System.out.println(notes);
		print_1D_Array(a);
	}
	public static void print_1D_Array(int[] a, String notes) {
		System.out.println(notes);
		print_1D_Array(a);
	}
	public static void print_1D_Array(double[] a, String notes) {
		System.out.println(notes);
		print_1D_Array(a);
	}
	public static <E> void print_1D_Array(E[] a, String notes) {
		System.out.println(notes);
		print_1D_Array(a);
	}
	public static void print_2D_Array(boolean[][] a, String notes) {
		System.out.println(notes);
		print_2D_Array(a);
	}
	public static void print_2D_Array(int[][] a, String notes) {
		System.out.println(notes);
		print_2D_Array(a);
	}
	public static void print_2D_Array(double[][] a, String notes) {
		System.out.println(notes);
		print_2D_Array(a);
	}
	public static <E> void print_2D_Array(E[][] a, String notes) {
		System.out.println(notes);
		print_2D_Array(a);
	}
	public static <E> void print_1D_List(List<E> a, String notes) {
		System.out.println(notes);
		print_1D_List(a);
	}
	public static <E> void print_2D_List(List<List<E>> a, String notes) {
		System.out.println(notes);
		print_2D_List(a);
	}
	
	public static String getStringOf_date(Calendar calendar) {
		String[] dayOfWeek = new String[]{"日","一","二","三","四","五","六"};
		String str = String.format("%d/%d/%d 星期%s", calendar.get(Calendar.YEAR), (calendar.get(calendar.MONTH) + 1),
				calendar.get(Calendar.DAY_OF_MONTH), dayOfWeek[calendar.get(Calendar.DAY_OF_WEEK) - 1]);
		return str;
	}
	public static String getStringOf_date(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return getStringOf_date(calendar);
	}
	public static String getStringOf_time(Calendar calendar) {
		String str = String.format("%02d:%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE),
				calendar.get(Calendar.SECOND));
		return str;
	}
	public static String getStringOf_time(Date date) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		return getStringOf_time(calendar);
	}
}
