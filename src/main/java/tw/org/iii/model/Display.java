package tw.org.iii.model;

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
}
