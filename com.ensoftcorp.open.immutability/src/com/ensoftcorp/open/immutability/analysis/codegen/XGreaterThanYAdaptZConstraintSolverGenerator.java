package com.ensoftcorp.open.immutability.analysis.codegen;

import static com.ensoftcorp.open.immutability.analysis.AnalysisUtilities.getTypes;
import static com.ensoftcorp.open.immutability.analysis.AnalysisUtilities.removeTypes;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import com.ensoftcorp.open.immutability.analysis.ImmutabilityTypes;
import com.ensoftcorp.open.immutability.log.Log;
import com.ensoftcorp.open.immutability.preferences.ImmutabilityPreferences;

public class XGreaterThanYAdaptZConstraintSolverGenerator {

	// all possible sets, 3 choose 3, 3 choose 2, and 3 choose 1
	private static final EnumSet<ImmutabilityTypes> SET1 = EnumSet.of(ImmutabilityTypes.MUTABLE, ImmutabilityTypes.POLYREAD, ImmutabilityTypes.READONLY);
	private static final EnumSet<ImmutabilityTypes> SET2 = EnumSet.of(ImmutabilityTypes.POLYREAD, ImmutabilityTypes.READONLY);
	private static final EnumSet<ImmutabilityTypes> SET3 = EnumSet.of(ImmutabilityTypes.MUTABLE, ImmutabilityTypes.POLYREAD);
	private static final EnumSet<ImmutabilityTypes> SET4 = EnumSet.of(ImmutabilityTypes.MUTABLE, ImmutabilityTypes.READONLY);
	private static final EnumSet<ImmutabilityTypes> SET5 = EnumSet.of(ImmutabilityTypes.READONLY);
	private static final EnumSet<ImmutabilityTypes> SET6 = EnumSet.of(ImmutabilityTypes.POLYREAD);
	private static final EnumSet<ImmutabilityTypes> SET7 = EnumSet.of(ImmutabilityTypes.MUTABLE);

	private static final ArrayList<EnumSet<ImmutabilityTypes>> sets = new ArrayList<EnumSet<ImmutabilityTypes>>();
	
	public static void main(String[] args){
		sets.add(SET1);
		sets.add(SET2);
		sets.add(SET3);
		sets.add(SET4);
		sets.add(SET5);
		sets.add(SET6);
		sets.add(SET7);
		
		int[] allSets = new int[]{1,2,3,4,5,6,7};
		
		for(int x : allSets){
			for(int y : allSets){
				for(int z : allSets){
					String result = getResult(sets.get(x-1), sets.get(y-1), sets.get(z-1));
					if(!result.equals("")){
						System.out.println("if(xTypes.equals(SET" + x + ")){");
						System.out.println("if(yTypes.equals(SET" + y + ")){");
						System.out.println("if(zTypes.equals(SET" + z + ")){");
						System.out.println(result);
						System.out.println("}");
						System.out.println("}");
						System.out.println("}");
					}
				}
			}
		}
		System.out.println("return false;");
	}
	
	private static int counter = 1;
	
	private static String getResult(EnumSet<ImmutabilityTypes> xTypes, EnumSet<ImmutabilityTypes> yTypes, EnumSet<ImmutabilityTypes> zTypes) {
		// process s(x)
		Set<ImmutabilityTypes> xTypesToRemove = EnumSet.noneOf(ImmutabilityTypes.class);
		for(ImmutabilityTypes xType : xTypes){
			boolean isSatisfied = false;
			satisfied:
			for(ImmutabilityTypes yType : yTypes){
				for(ImmutabilityTypes zType : zTypes){
					ImmutabilityTypes yAdaptedZ = ImmutabilityTypes.getAdaptedFieldViewpoint(yType, zType);
					if(xType.compareTo(yAdaptedZ) >= 0){
						isSatisfied = true;
						break satisfied;
					}
				}
			}
			if(!isSatisfied){
				xTypesToRemove.add(xType);
			}
		}
		
		// process s(y)
		Set<ImmutabilityTypes> yTypesToRemove = EnumSet.noneOf(ImmutabilityTypes.class);
		for(ImmutabilityTypes yType : yTypes){
			boolean isSatisfied = false;
			satisfied:
			for(ImmutabilityTypes xType : xTypes){
				for(ImmutabilityTypes zType : zTypes){
					ImmutabilityTypes yAdaptedZ = ImmutabilityTypes.getAdaptedFieldViewpoint(yType, zType);
					if(xType.compareTo(yAdaptedZ) >= 0){
						isSatisfied = true;
						break satisfied;
					}
				}
			}
			if(!isSatisfied){
				yTypesToRemove.add(yType);
			}
		}
		
		// process s(z)
		Set<ImmutabilityTypes> zTypesToRemove = EnumSet.noneOf(ImmutabilityTypes.class);
		for(ImmutabilityTypes zType : zTypes){
			boolean isSatisfied = false;
			satisfied:
			for(ImmutabilityTypes xType : xTypes){
				for(ImmutabilityTypes yType : yTypes){
					ImmutabilityTypes yAdaptedZ = ImmutabilityTypes.getAdaptedFieldViewpoint(yType, zType);
					if(xType.compareTo(yAdaptedZ) >= 0){
						isSatisfied = true;
						break satisfied;
					}
				}
			}
			if(!isSatisfied){
				zTypesToRemove.add(zType);
			}
		}
		
		if(xTypesToRemove.isEmpty() && yTypesToRemove.isEmpty() && zTypesToRemove.isEmpty()){
			String prefix = "if(ImmutabilityPreferences.isConstraintProfilingEnabled()) incrementCounter(" + counter++ + ");\n";
			return prefix + "return false;";
		} else {
			if(xTypesToRemove.isEmpty() && yTypesToRemove.isEmpty()){
				String prefix = "if(ImmutabilityPreferences.isConstraintProfilingEnabled()) incrementCounter(" + counter++ + ");\n";
				return prefix + "return removeTypes(z, EnumSet.of(" + getSetString(zTypesToRemove) + "));";
			} else if(xTypesToRemove.isEmpty() && zTypesToRemove.isEmpty()){
				String prefix = "if(ImmutabilityPreferences.isConstraintProfilingEnabled()) incrementCounter(" + counter++ + ");\n";
				return prefix + "return removeTypes(y, EnumSet.of(" + getSetString(yTypesToRemove) + "));";
			} else if(yTypesToRemove.isEmpty() && zTypesToRemove.isEmpty()){
				String prefix = "if(ImmutabilityPreferences.isConstraintProfilingEnabled()) incrementCounter(" + counter++ + ");\n";
				return prefix + "return removeTypes(x, EnumSet.of(" + getSetString(xTypesToRemove) + "));";
			} else {
				if(xTypesToRemove.isEmpty()){
					String result = "";
					String prefix = "if(ImmutabilityPreferences.isConstraintProfilingEnabled()) incrementCounter(" + counter++ + ");";
					result += prefix + "\n";
					result += "boolean yTypesChanged = removeTypes(y, EnumSet.of(" + getSetString(yTypesToRemove) + "));\n";
					result += "boolean zTypesChanged = removeTypes(z, EnumSet.of(" + getSetString(zTypesToRemove) + "));\n";
					result += "return yTypesChanged || zTypesChanged;";
					return result;
				} else if(yTypesToRemove.isEmpty()){
					String result = "";
					String prefix = "if(ImmutabilityPreferences.isConstraintProfilingEnabled()) incrementCounter(" + counter++ + ");";
					result += prefix + "\n";
					result += "boolean xTypesChanged = removeTypes(x, EnumSet.of(" + getSetString(xTypesToRemove) + "));\n";
					result += "boolean zTypesChanged = removeTypes(z, EnumSet.of(" + getSetString(zTypesToRemove) + "));\n";
					result += "return xTypesChanged || zTypesChanged;";
					return result;
				} else if(zTypesToRemove.isEmpty()){
					String result = "";
					String prefix = "if(ImmutabilityPreferences.isConstraintProfilingEnabled()) incrementCounter(" + counter++ + ");";
					result += prefix + "\n";
					result += "boolean xTypesChanged = removeTypes(x, EnumSet.of(" + getSetString(xTypesToRemove) + "));\n";
					result += "boolean yTypesChanged = removeTypes(y, EnumSet.of(" + getSetString(yTypesToRemove) + "));\n";
					result += "return xTypesChanged || yTypesChanged;";
					return result;
				} else {
					String result = "";
					String prefix = "if(ImmutabilityPreferences.isConstraintProfilingEnabled()) incrementCounter(" + counter++ + ");";
					result += prefix + "\n";
					result += "boolean xTypesChanged = removeTypes(x, EnumSet.of(" + getSetString(xTypesToRemove) + "));\n";
					result += "boolean yTypesChanged = removeTypes(y, EnumSet.of(" + getSetString(yTypesToRemove) + "));\n";
					result += "boolean zTypesChanged = removeTypes(z, EnumSet.of(" + getSetString(zTypesToRemove) + "));\n";
					result += "return xTypesChanged || yTypesChanged || zTypesChanged;";
					return result;
				}
			}
		}
	}
	
	private static String getSetString(Set<ImmutabilityTypes> xTypesToRemove){
		String result = "";
		String prefix = "";
		for(ImmutabilityTypes type : xTypesToRemove){
			result += prefix + "ImmutabilityTypes." + type.toString();
			prefix = ", ";
		}
		return result;
	}
	
}
