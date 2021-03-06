package com.ensoftcorp.open.immutability.analysis.checkers;

import static com.ensoftcorp.open.immutability.analysis.AnalysisUtilities.getTypes;

import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.immutability.analysis.solvers.XFieldAdaptYGreaterThanEqualZConstraintSolver;
import com.ensoftcorp.open.immutability.analysis.solvers.XGreaterThanEqualYConstraintSolver;
import com.ensoftcorp.open.immutability.log.Log;
import com.ensoftcorp.open.immutability.preferences.ImmutabilityPreferences;

public class BasicAssignmentChecker {

	/**
	 * Solves and satisfies constraints for Type Rule 2 - TASSIGN
	 * Let, x = y
	 * 
	 * @param x The reference being written to
	 * @param y The reference be read from
	 * @return
	 */
	public static boolean handleAssignment(Node x, Node y) {
		if(x==null){
			Log.warning("x is null!");
			return false;
		}
		
		if(y==null){
			Log.warning("y is null!");
			return false;
		}
		
		if(ImmutabilityPreferences.isInferenceRuleLoggingEnabled()) {
			String values = "x:" + getTypes(x).toString() + ", y:" + getTypes(y).toString();
			Log.info("TASSIGN (x=y, x=" + x.getAttr(XCSG.name) + ", y=" + y.getAttr(XCSG.name) + ")\n" + values);
		}
		
		if(y.taggedWith(XCSG.InstanceVariable) && ImmutabilityPreferences.isFieldAdaptationsEnabled()){
			// treat x :> y, as x fadapt y :> y
			if(ImmutabilityPreferences.isInferenceRuleLoggingEnabled()) {
				Log.info("Processing Instance Variable Assignment Constraint x fadapt y :> y");
			}
			return XFieldAdaptYGreaterThanEqualZConstraintSolver.satisify(x, y, y);
		} else {
			if(ImmutabilityPreferences.isInferenceRuleLoggingEnabled()) {
				Log.info("Processing Constraint x :> y");
			}
			return XGreaterThanEqualYConstraintSolver.satisify(x, y);
		}
	}
	
}
