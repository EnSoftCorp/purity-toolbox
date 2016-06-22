package com.ensoftcorp.open.purity.analysis;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.TreeSet;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.purity.log.Log;
import com.ensoftcorp.open.purity.preferences.PurityPreferences;

public class Utilities {
	
//	// caching for some common graph types
//	private static boolean cacheInitialized = false;
//	private static AtlasSet<GraphElement> defaultReadonlyTypes;
//	
//	private static void initializeCache(IProgressMonitor monitor) {
//		// initialize the cache of default readonly types
//		defaultReadonlyTypes = new AtlasHashSet<GraphElement>();
//		
//		// autoboxing
//		defaultReadonlyTypes.add(Common.typeSelect("java.lang", "Integer").eval().nodes().getFirst());
//		defaultReadonlyTypes.add(Common.typeSelect("java.lang", "Long").eval().nodes().getFirst());
//		defaultReadonlyTypes.add(Common.typeSelect("java.lang", "Short").eval().nodes().getFirst());
//		defaultReadonlyTypes.add(Common.typeSelect("java.lang", "Boolean").eval().nodes().getFirst());
//		defaultReadonlyTypes.add(Common.typeSelect("java.lang", "Byte").eval().nodes().getFirst());
//		defaultReadonlyTypes.add(Common.typeSelect("java.lang", "Double").eval().nodes().getFirst());
//		defaultReadonlyTypes.add(Common.typeSelect("java.lang", "Float").eval().nodes().getFirst());
//		defaultReadonlyTypes.add(Common.typeSelect("java.lang", "Character").eval().nodes().getFirst());
//		
//		// a few other objects are special cases for all practical purposes
//		defaultReadonlyTypes.add(Common.typeSelect("java.lang", "String").eval().nodes().getFirst());
//		defaultReadonlyTypes.add(Common.typeSelect("java.lang", "Number").eval().nodes().getFirst());
//		defaultReadonlyTypes.add(Common.typeSelect("java.util.concurrent.atomic", "AtomicInteger").eval().nodes().getFirst());
//		defaultReadonlyTypes.add(Common.typeSelect("java.util.concurrent.atomic", "AtomicLong").eval().nodes().getFirst());
//		defaultReadonlyTypes.add(Common.typeSelect("java.math", "BigDecimal").eval().nodes().getFirst());
//		defaultReadonlyTypes.add(Common.typeSelect("java.math", "BigInteger").eval().nodes().getFirst());
//	}
//	
//	/**
//	 * Returns true if the given type is a default readonly type
//	 * @param type
//	 * @return
//	 */
//	public static boolean isDefaultReadonlyType(GraphElement type) {
//		if(type == null){
//			return false;
//		}
//		if(!cacheInitialized){
//			initializeCache(new NullProgressMonitor());
//		}
//		return type.taggedWith(XCSG.Primitive) || defaultReadonlyTypes.contains(type);
//	}
	
	/**
	 * Used as an attribute key to temporarily compute the potential immutability qualifiers
	 */
	public static final String IMMUTABILITY_QUALIFIERS = "IMMUTABILITY_QUALIFIERS";
	
	// TODO: bug EnSoft to make tags like these...
	public static final String CLASS_VARIABLE_ASSIGNMENT = "CLASS_VARIABLE_ASSIGNMENT";
	public static final String CLASS_VARIABLE_VALUE = "CLASS_VARIABLE_VALUE";
	public static final String CLASS_VARIABLE_ACCESS = "CLASS_VARIABLE_ACCESS";
	
	// TODO: pester EnSoft to remove these...or at least unify with source graph format
	public static final String DATAFLOW_DISPLAY_NODE = "DATAFLOW_DISPLAY_NODE";
	
	public static final String DUMMY_ASSIGNMENT_NODE = "DUMMY_ASSIGNMENT_NODE";
	public static final String DUMMY_RETURN_NODE = "DUMMY_RETURN_NODE";
	
	/**
	 * Adds DUMMY_RETURN_NODE to void methods and DUMMY_ASSIGNMENT_NODE from unassigned callsites to a dummy assignment node
	 */
	public static void addDummyReturnAssignments(){
		if(PurityPreferences.isGeneralLoggingEnabled()) Log.info("Adding dummy return assignments...");
		Q returnsEdges = Common.universe().edgesTaggedWithAny(XCSG.Returns).retainEdges();
		Q voidMethods = returnsEdges.predecessors(Common.types("void"));
		for(GraphElement voidMethod : voidMethods.eval().nodes()){
			GraphElement returnValue = Graph.U.createNode();
			returnValue.putAttr(XCSG.name, DUMMY_RETURN_NODE);
			returnValue.tag(XCSG.ReturnValue);
			returnValue.tag(DUMMY_RETURN_NODE);
			// create a contains edge from the void method to the return value
			GraphElement containsEdge = Graph.U.createEdge(voidMethod, returnValue);
			containsEdge.tag(XCSG.Contains);
		}
		
		// TODO: enable after known bug in Atlas is fixed
//		// sanity check (all methods have a return value)
//		Q allMethods = Common.universe().nodesTaggedWithAny(XCSG.Method);
//		Q returnValues = Common.universe().nodesTaggedWithAny(XCSG.ReturnValue);
//		long numMissingReturns = allMethods.difference(returnValues.parent()).eval().nodes().size();
//		if(numMissingReturns > 0){
//			throw new RuntimeException("There are " + numMissingReturns + " missing return value nodes!");
//		}
		
		Q callsites = Common.universe().nodesTaggedWithAny(XCSG.CallSite);
		Q localDataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.LocalDataFlow);
		Q interproceduralDataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.InterproceduralDataFlow);
		Q assignments = Common.universe().nodesTaggedWithAny(XCSG.Assignment);
		Q assignedCallsites = localDataFlowEdges.predecessors(assignments).nodesTaggedWithAny(XCSG.CallSite);
		Q unassignedCallsites = callsites.difference(assignedCallsites);
		for(GraphElement unassignedCallsite : unassignedCallsites.eval().nodes()){
			GraphElement dummyAssignmentNode = Graph.U.createNode();
			dummyAssignmentNode.putAttr(XCSG.name, DUMMY_ASSIGNMENT_NODE);
			dummyAssignmentNode.tag(XCSG.Assignment);
			dummyAssignmentNode.tag(DUMMY_ASSIGNMENT_NODE);
			// create edge from unassigned callsite to the dummy assignment node
			GraphElement localDataFlowEdge = Graph.U.createEdge(unassignedCallsite, dummyAssignmentNode);
			localDataFlowEdge.tag(XCSG.LocalDataFlow);
			
			// create a contains edge from the callsites parent to the dummy assignment node
			GraphElement parent = Common.toQ(unassignedCallsite).parent().eval().nodes().getFirst();
			GraphElement containsEdge = Graph.U.createEdge(parent, dummyAssignmentNode);
			containsEdge.tag(XCSG.Contains);
			
			// if the unassigned callsite does not have an incoming interprocedural data flow edge
			// then it must be a void method in which case we need to link it up with the corresponding
			// dummy return node. Since the dummy nodes are just place holders for readonly types,
			// its not terribly important to completely resolve dynamic dispatches and we can just link
			// to the dummy return node of the signature method
			if(interproceduralDataFlowEdges.predecessors(Common.toQ(unassignedCallsite)).eval().nodes().isEmpty()){
				GraphElement method = getInvokedMethodSignature(unassignedCallsite);
				GraphElement returnValue = Common.toQ(method).children().nodesTaggedWithAny(XCSG.ReturnValue).eval().nodes().getFirst();
				GraphElement interproceduralDataFlowEdge = Graph.U.createEdge(returnValue, unassignedCallsite);
				interproceduralDataFlowEdge.tag(XCSG.InterproceduralDataFlow);
			}
		}
		
		// sanity check (all callsites are assigned to an assignment node)
		long numMissingCallsiteAssignments = callsites.difference(localDataFlowEdges.predecessors(assignments)).eval().nodes().size();
		if(numMissingCallsiteAssignments > 0){
			throw new RuntimeException("There are " + numMissingCallsiteAssignments + " missing callsite assignments!");
		}
		
		// sanity check (all callsites get a value from a return value)
		Q returnValuesToCallsites = interproceduralDataFlowEdges.successors(Common.universe().nodesTaggedWithAny(XCSG.ReturnValue)).nodesTaggedWithAny(XCSG.CallSite);
		long numMissingCallsiteReturns = callsites.difference(returnValuesToCallsites).eval().nodes().size();
		if(numMissingCallsiteReturns > 0){
			throw new RuntimeException("There are " + numMissingCallsiteReturns + " missing callsite returns!");
		}
	}
	
	/**
	 * Removes DUMMY_RETURN_NODE and DUMMY_ASSIGNMENT_NODE nodes and any edges connected to them
	 */
	public static void removeDummyReturnAssignments(){
		if(PurityPreferences.isGeneralLoggingEnabled()) Log.info("Removing dummy return assignments...");
		// just need to remove the nodes
		// edges connected to the new nodes will be removed once the nodes are removed
		Q dummyNodes = Common.universe().nodesTaggedWithAny(DUMMY_RETURN_NODE, DUMMY_ASSIGNMENT_NODE);
		for(GraphElement dummyNode : dummyNodes.eval().nodes()){
			Graph.U.delete(dummyNode);
		}
	}
	
	/**
	 * Adds DATAFLOW_DISPLAY_NODE tags to display nodes
	 * Data flow display nodes are added for graph display reasons...
	 */
	public static void addDataFlowDisplayNodeTags() {
		if(PurityPreferences.isGeneralLoggingEnabled()) Log.info("Adding data flow display node tags...");
		ArrayList<String> nonDataFlowDisplayNodeTags = new ArrayList<String>();
		for(String tag : XCSG.HIERARCHY.childrenOfOneParent(XCSG.DataFlow_Node)){
			nonDataFlowDisplayNodeTags.add(tag);
		}
		String[] nonDataFlowDisplayNodeTagArray = new String[nonDataFlowDisplayNodeTags.size()];
		nonDataFlowDisplayNodeTags.toArray(nonDataFlowDisplayNodeTagArray);
		Q dataFlowNodes = Common.universe().nodesTaggedWithAny(XCSG.DataFlow_Node);
		Q classVariableAccessNodes = Common.universe().nodesTaggedWithAny(CLASS_VARIABLE_ACCESS);
		Q nonVanillaDataFlowNodes = Common.universe().nodesTaggedWithAny(nonDataFlowDisplayNodeTagArray);
		for(GraphElement dataFlowDisplayNode : dataFlowNodes.difference(classVariableAccessNodes, nonVanillaDataFlowNodes).eval().nodes()){
			dataFlowDisplayNode.tag(DATAFLOW_DISPLAY_NODE);
		}
		
		// sanity check, better to fail fast here than later...
		Q localDataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.LocalDataFlow);
		Q displayNodes = Common.universe().nodesTaggedWithAny(DATAFLOW_DISPLAY_NODE);
		
		// data flow display nodes should be accessible only from a local data flow edge
		Q localDataFlowDisplayNodes = localDataFlowEdges.reverseStep(displayNodes).retainEdges();
		if(localDataFlowDisplayNodes.intersection(displayNodes).eval().nodes().size() != displayNodes.eval().nodes().size()){
			throw new RuntimeException("Unexpected data flow display nodes!");
		}
		
		// data flow display nodes parents should not also be data flow display nodes
		Q dataFlowDisplayNodeParents = localDataFlowEdges.predecessors(displayNodes);
		if(!dataFlowDisplayNodeParents.nodesTaggedWithAny(DATAFLOW_DISPLAY_NODE).eval().nodes().isEmpty()){
			throw new RuntimeException("Unexpected data flow display nodes parents!");
		}
	}
	
	/**
	 * Removes DATAFLOW_DISPLAY_NODE tags to display nodes
	 */
	public static void removeDataFlowDisplayNodeTags() {
		if(PurityPreferences.isGeneralLoggingEnabled()) Log.info("Removing data flow display node tags...");
		AtlasSet<GraphElement> dataFlowDisplayNodes = Common.universe().nodesTaggedWithAny(DATAFLOW_DISPLAY_NODE).eval().nodes();
		for(GraphElement dataFlowDisplayNode : dataFlowDisplayNodes){
			dataFlowDisplayNode.tags().remove(DATAFLOW_DISPLAY_NODE);
		}
	}
	
	public static AtlasSet<GraphElement> getDisplayNodeReferences(GraphElement displayNode){
		Q localDataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.LocalDataFlow);
		Q dataFlowDisplayNodeParents = localDataFlowEdges.predecessors(Common.toQ(displayNode));
		return dataFlowDisplayNodeParents.eval().nodes();
	}
	
	/**
	 * Adds CLASS_VARIABLE_ASSIGNMENT, CLASS_VARIABLE_VALUE, and CLASS_VARIABLE_ACCESS
	 * tags to reads/writes on static variables
	 */
	public static void addClassVariableAccessTags() {
		if(PurityPreferences.isGeneralLoggingEnabled()) Log.info("Adding class variable access tags...");
		Q classVariables = Common.universe().nodesTaggedWithAny(XCSG.ClassVariable);
		Q interproceduralDataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.InterproceduralDataFlow);
		AtlasSet<GraphElement> classVariableAssignments = interproceduralDataFlowEdges.predecessors(classVariables).eval().nodes();
		for(GraphElement classVariableAssignment : classVariableAssignments){
			classVariableAssignment.tag(CLASS_VARIABLE_ASSIGNMENT);
			classVariableAssignment.tag(CLASS_VARIABLE_ACCESS);
		}
		AtlasSet<GraphElement> classVariableValues = interproceduralDataFlowEdges.successors(classVariables).eval().nodes();
		for(GraphElement classVariableValue : classVariableValues){
			classVariableValue.tag(CLASS_VARIABLE_VALUE);
			classVariableValue.tag(CLASS_VARIABLE_ACCESS);
		}
	}
	
	/**
	 * Removes CLASS_VARIABLE_ASSIGNMENT, CLASS_VARIABLE_VALUE, and CLASS_VARIABLE_ACCESS
	 * tags to reads/writes on static variables
	 */
	public static void removeClassVariableAccessTags() {
		if(PurityPreferences.isGeneralLoggingEnabled()) Log.info("Removing class variable access tags...");
		Q classVariables = Common.universe().nodesTaggedWithAny(XCSG.ClassVariable);
		Q interproceduralDataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.InterproceduralDataFlow);
		AtlasSet<GraphElement> classVariableAssignments = interproceduralDataFlowEdges.predecessors(classVariables).eval().nodes();
		for(GraphElement classVariableAssignment : classVariableAssignments){
			classVariableAssignment.tags().remove(CLASS_VARIABLE_ASSIGNMENT);
			classVariableAssignment.tags().remove(CLASS_VARIABLE_ACCESS);
		}
		AtlasSet<GraphElement> classVariableValues = interproceduralDataFlowEdges.successors(classVariables).eval().nodes();
		for(GraphElement classVariableValue : classVariableValues){
			classVariableValue.tags().remove(CLASS_VARIABLE_VALUE);
			classVariableValue.tags().remove(CLASS_VARIABLE_ACCESS);
		}
	}
	
	/**
	 * Given a callsite this method returns the invoked method signature
	 * @param callsite
	 * @return
	 */
	public static GraphElement getInvokedMethodSignature(GraphElement callsite) {
		// XCSG.InvokedSignature connects a dynamic dispatch to its signature method
		// XCSG.InvokedFunction connects a static dispatch to it actual target method
		Q invokedEdges = Common.universe().edgesTaggedWithAny(XCSG.InvokedSignature, XCSG.InvokedFunction);
		GraphElement method = invokedEdges.successors(Common.toQ(callsite)).eval().nodes().getFirst();
		return method;
	}
	
	/**
	 * Sets the type qualifier for a graph element
	 * @param ge
	 * @param qualifier
	 * @return Returns true if the type qualifier changed
	 */
	public static boolean removeTypes(GraphElement ge, Set<ImmutabilityTypes> typesToRemove){
		Set<ImmutabilityTypes> typeSet = getTypes(ge);
		String logMessage = "Remove: " + typesToRemove.toString() + " from " + typeSet.toString() + " for " + ge.getAttr(XCSG.name);
		boolean typesChanged = typeSet.removeAll(typesToRemove);
		if(typesChanged){
			if(PurityPreferences.isDebugLoggingEnabled()) Log.info(logMessage);
			if(PurityPreferences.isDebugLoggingEnabled() && typeSet.isEmpty()) Log.warning("Remove result in an empty type set.");
		}
		return typesChanged;
	}
	
	/**
	 * Sets the type qualifier for a graph element
	 * @param ge
	 * @param qualifier
	 * @return Returns true if the type qualifier changed
	 */
	public static boolean removeTypes(GraphElement ge, ImmutabilityTypes... types){
		EnumSet<ImmutabilityTypes> typesToRemove = EnumSet.noneOf(ImmutabilityTypes.class);
		for(ImmutabilityTypes type : types){
			typesToRemove.add(type);
		}
		return removeTypes(ge, typesToRemove);
	}
	
	@SuppressWarnings("unchecked")
	public static Set<ImmutabilityTypes> getTypes(GraphElement ge){
		if(ge.hasAttr(IMMUTABILITY_QUALIFIERS)){
			return (Set<ImmutabilityTypes>) ge.getAttr(IMMUTABILITY_QUALIFIERS);
		} else {
			EnumSet<ImmutabilityTypes> qualifiers = getDefaultTypes(ge);
			ge.putAttr(IMMUTABILITY_QUALIFIERS, qualifiers);
			return qualifiers;
		}
	}
	
	public static GraphElement getObjectType(GraphElement ge) {
		Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
		return typeOfEdges.successors(Common.toQ(ge)).eval().nodes().getFirst();
	}
	
	public static AtlasSet<GraphElement> parseReferences(GraphElement ge){
		if(PurityPreferences.isDebugLoggingEnabled()) Log.info("Parsing reference for " + ge.address().toAddressString());
		AtlasSet<GraphElement> parsedReferences = new AtlasHashSet<GraphElement>();
		TreeSet<GraphElement> worklist = new TreeSet<GraphElement>();
		worklist.add(ge);
		
		Q dataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge);
		Q interproceduralDataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.InterproceduralDataFlow);
		
		while(!worklist.isEmpty()){
			GraphElement reference = worklist.pollFirst();
			if(reference != null && needsProcessing(reference)){
				if(reference.taggedWith(XCSG.Cast)){
					for(GraphElement workItem : dataFlowEdges.predecessors(Common.toQ(reference)).eval().nodes()){
						worklist.add(workItem);
					}
					continue;
				}
				
				if(reference.taggedWith(DATAFLOW_DISPLAY_NODE)){
					for(GraphElement workItem : Utilities.getDisplayNodeReferences(reference)){
						worklist.add(workItem);
					}
					continue;
				}
				
				if(reference.taggedWith(XCSG.CallSite)){
					// parse return, a callsite on a callsite must be a callsite on the resulting object from the first callsite
					GraphElement method = Utilities.getInvokedMethodSignature(reference);
					GraphElement ret = Common.toQ(method).children().nodesTaggedWithAny(XCSG.ReturnValue).eval().nodes().getFirst();
					reference = ret;
					continue;
				}
				
				// get the field for instance and class variable assignments
				if(reference.taggedWith(XCSG.InstanceVariableAssignment) || reference.taggedWith(Utilities.CLASS_VARIABLE_ASSIGNMENT)){
					for(GraphElement workItem : interproceduralDataFlowEdges.successors(Common.toQ(reference)).eval().nodes()){
						worklist.add(workItem);
					}
					continue;
				}
				
				// get the field for instance and class variable values
				if(reference.taggedWith(XCSG.InstanceVariableValue) || reference.taggedWith(Utilities.CLASS_VARIABLE_VALUE)){
					for(GraphElement workItem : interproceduralDataFlowEdges.predecessors(Common.toQ(reference)).eval().nodes()){
						worklist.add(workItem);
					}
					continue;
				}
				
				// get the array components being written to
				if(reference.taggedWith(XCSG.ArrayWrite)){
					for(GraphElement workItem : interproceduralDataFlowEdges.successors(Common.toQ(reference)).eval().nodes()){
						worklist.add(workItem);
					}
					continue;
				}
				
				// get the array components being read from
				if(reference.taggedWith(XCSG.ArrayRead)){
					for(GraphElement workItem : interproceduralDataFlowEdges.predecessors(Common.toQ(reference)).eval().nodes()){
						worklist.add(workItem);
					}
					continue;
				}
				
				String message = "Unhandled reference type for GraphElement " + ge.address().toAddressString();
				RuntimeException e = new RuntimeException(message);
				Log.error(message, e);
				throw e;
			} else {
				if(reference == null){
					String message = "Null reference for GraphElement " + ge.address().toAddressString();
					RuntimeException e = new RuntimeException(message);
					Log.error(message, e);
					throw e;
				} else {
					parsedReferences.add(reference);
				}
			}
		}
		
		return parsedReferences;
	}
	
	private static boolean needsProcessing(GraphElement ge){
		if(ge.taggedWith(DATAFLOW_DISPLAY_NODE)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.Cast)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.CallSite)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.InstanceVariableAccess) || ge.taggedWith(Utilities.CLASS_VARIABLE_ACCESS)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.ArrayAccess)){
			return true;
		}
		
		return !isTypable(ge);
	}
	
	public static boolean isTypable(GraphElement ge){
		// invalid types
		if(ge.taggedWith(XCSG.InstanceVariableAccess) || ge.taggedWith(Utilities.CLASS_VARIABLE_ACCESS)){
			return false;
		}
		
		if(ge.taggedWith(XCSG.ArrayAccess)){
			return false;
		}
		
		// valid types
		if(ge.taggedWith(DUMMY_ASSIGNMENT_NODE) || ge.taggedWith(DUMMY_RETURN_NODE)){
			// these are dummy read only nodes
			return true;
		}
		
		if(ge.taggedWith(XCSG.Null)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.Literal)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.Instantiation) || ge.taggedWith(XCSG.ArrayInstantiation)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.Method)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.Identity)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.Parameter)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.ReturnValue)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.InstanceVariable) || ge.taggedWith(XCSG.ClassVariable)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.ArrayComponents)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.ParameterPass)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.Operator)){
			return true;
		} 
		
		if(ge.taggedWith(XCSG.CaughtValue)){
			return true;
		} 
		
		if(ge.taggedWith(XCSG.ElementFromCollection)){
			return true;
		}
		
		if(ge.taggedWith(XCSG.Assignment)){
			if(!ge.taggedWith(XCSG.InstanceVariableAssignment) && !ge.taggedWith(Utilities.CLASS_VARIABLE_ASSIGNMENT)){
				return true;
			}
		}
		
//		if(isDefaultReadonlyType(getObjectType(ge))){
//			return true;
//		}
		
		// something made it through the gap...
		return false;
	}
	
	public static EnumSet<ImmutabilityTypes> getDefaultTypes(GraphElement ge) {
		if(!isTypable(ge)){
			RuntimeException e = new RuntimeException("Unexpected graph element: " + ge.address());
			Log.error("Unexpected graph element: " + ge.address(), e);
			throw e;
		}
		
		EnumSet<ImmutabilityTypes> qualifiers = EnumSet.noneOf(ImmutabilityTypes.class);
		
		if(ge.taggedWith(DUMMY_ASSIGNMENT_NODE) || ge.taggedWith(DUMMY_RETURN_NODE)){
			// these are dummy read only nodes that help to provide context sensitivity
			// in unassigned callsites or void methods
			qualifiers.add(ImmutabilityTypes.READONLY);
			qualifiers.add(ImmutabilityTypes.POLYREAD);
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		} else if(ge.taggedWith(XCSG.Null)){
			// null does not modify the stack or heap so it is readonly
			// note however that assignments of nulls to a field can still mutate an object
			qualifiers.add(ImmutabilityTypes.READONLY);
			// however in order to satisfy constraints the other types should be initialized
			qualifiers.add(ImmutabilityTypes.POLYREAD);
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		} else if(ge.taggedWith(XCSG.Literal)){
			// several java objects are readonly for all practical purposes
			// however in order to satisfy constraints the other types should be initialized
			qualifiers.add(ImmutabilityTypes.READONLY);
			qualifiers.add(ImmutabilityTypes.POLYREAD);
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		} else if(ge.taggedWith(XCSG.Instantiation) || ge.taggedWith(XCSG.ArrayInstantiation)){
			// Type Rule 1 - TNEW
			// return type of a constructor is only mutable
			// x = new C(); // no effect on qualifier to x
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		} else if(ge.taggedWith(XCSG.ReturnValue)){
			// Section 2.4 of Reference 1
			// "Method returns are initialized S(ret) = {readonly, polyread} for each method m"
			qualifiers.add(ImmutabilityTypes.READONLY);
			qualifiers.add(ImmutabilityTypes.POLYREAD);
		} else if (ge.taggedWith(XCSG.Parameter)){
			qualifiers.add(ImmutabilityTypes.READONLY);
			qualifiers.add(ImmutabilityTypes.POLYREAD);
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		} else if(ge.taggedWith(XCSG.Identity)){
			qualifiers.add(ImmutabilityTypes.READONLY);
			// TODO: this is causing problems, but...the reference 1 specifically says its an valid type...
//			qualifiers.add(ImmutabilityTypes.POLYREAD);
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		} else if(ge.taggedWith(XCSG.InstanceVariable)){
			// Section 2.4 of Reference 1
			// "Fields are initialized to S(f) = {readonly, polyread}"
			qualifiers.add(ImmutabilityTypes.READONLY);
			qualifiers.add(ImmutabilityTypes.POLYREAD);
		} else if(ge.taggedWith(XCSG.ClassVariable)){
			// Section 3 of Reference 1
			// static fields are initialized to S(sf) = {readonly, mutable}
			qualifiers.add(ImmutabilityTypes.READONLY);
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		} else if(ge.taggedWith(XCSG.Method)){
			// Section 3 of Reference 1
			// methods can have a static type of {readonly, polyread, mutable}
			qualifiers.add(ImmutabilityTypes.READONLY);
			qualifiers.add(ImmutabilityTypes.POLYREAD);
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		} else if(ge.taggedWith(XCSG.Operator)){
			// the result of a primitive operation on primitives or primitive references is always readonly
			qualifiers.add(ImmutabilityTypes.READONLY);
			qualifiers.add(ImmutabilityTypes.POLYREAD);
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		} else if(ge.taggedWith(XCSG.ArrayComponents)){
			qualifiers.add(ImmutabilityTypes.READONLY);
//			qualifiers.add(ImmutabilityTypes.POLYREAD); // an array component is basically a local reference, TODO: what about array fields?
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		} else if(ge.taggedWith(XCSG.CaughtValue)){
			// caught exceptions could be polyread since they could come from multiple call stacks
			qualifiers.add(ImmutabilityTypes.READONLY);
			qualifiers.add(ImmutabilityTypes.POLYREAD); 
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		}  else if(ge.taggedWith(XCSG.ElementFromCollection)){
			// TODO: should probably treat these like array components (mutations to these mutate the collection)
			qualifiers.add(ImmutabilityTypes.READONLY);
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		} else if(ge.taggedWith(XCSG.ParameterPass)){
			// Section 2.4 of Reference 1
			// "All other references are initialized to the maximal
			// set of qualifiers, i.e. S(x) = {readonly, polyread, mutable}"
			// But, what does it mean for a local reference to be polyread? ~Ben
			qualifiers.add(ImmutabilityTypes.READONLY);
			qualifiers.add(ImmutabilityTypes.MUTABLE);
		} else if(ge.taggedWith(XCSG.Assignment)){
			if(!ge.taggedWith(XCSG.InstanceVariableAssignment) && !ge.taggedWith(Utilities.CLASS_VARIABLE_ASSIGNMENT)){
				// could be a local reference
				// Section 2.4 of Reference 1
				// "All other references are initialized to the maximal
				// set of qualifiers, i.e. S(x) = {readonly, polyread, mutable}"
				// But, what does it mean for a local reference to be polyread? ~Ben
				qualifiers.add(ImmutabilityTypes.READONLY);
				qualifiers.add(ImmutabilityTypes.MUTABLE);
			}
		}
		
//		else if(isDefaultReadonlyType(Utilities.getObjectType(ge))){
//			// several java objects are readonly for all practical purposes
//			// however in order to satisfy constraints the other types should be initialized
//			qualifiers.add(ImmutabilityTypes.READONLY);
//			qualifiers.add(ImmutabilityTypes.POLYREAD);
//			qualifiers.add(ImmutabilityTypes.MUTABLE);
//		} 
		
		else {
			RuntimeException e = new RuntimeException("Unexpected graph element: " + ge.address());
			Log.error("Unexpected graph element: " + ge.address(), e);
			throw e;
		}
		return qualifiers;
	}
	
	/**
	 * Returns the fields or local variables accessed for an instance variable access
	 * @param fieldAccess
	 * @return
	 */
	public static AtlasSet<GraphElement> getAccessedContainers(GraphElement fieldAccess){
		Q instanceVariableAccessedEdges = Common.universe().edgesTaggedWithAny(XCSG.InstanceVariableAccessed);
		Q variablesAccessed = instanceVariableAccessedEdges.reverse(Common.toQ(fieldAccess));
		Q instanceVariablesAccessed = variablesAccessed.nodesTaggedWithAny(XCSG.InstanceVariableAccess);
		Q classVariablesAccessed = variablesAccessed.nodesTaggedWithAny(CLASS_VARIABLE_ACCESS);
		Q localVariables = variablesAccessed.difference(instanceVariablesAccessed, classVariablesAccessed);
		Q interproceduralDataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.InterproceduralDataFlow);
		Q fieldsAccessed = interproceduralDataFlowEdges.predecessors(instanceVariablesAccessed.union(classVariablesAccessed));
		return localVariables.union(fieldsAccessed).eval().nodes();
	}
	
	/**
	 * Returns the containing method of a given graph element or null if one is not found
	 * @param ge
	 * @return
	 */
	public static GraphElement getContainingMethod(GraphElement ge) {
		// NOTE: the enclosing method may be two steps or more above
		return getContainingNode(ge, XCSG.Method);
	}
	
	/**
	 * Find the next immediate containing node with the given tag.
	 * 
	 * @param node 
	 * @param containingTag
	 * @return the next immediate containing node, or null if none exists; never returns the given node
	 */
	public static GraphElement getContainingNode(GraphElement node, String containingTag) {
		if(node == null){
			return null;
		}
		
		while(true) {
			GraphElement containsEdge = Graph.U.edges(node, NodeDirection.IN).taggedWithAll(XCSG.Contains).getFirst();
			if(containsEdge == null){
				return null;
			}
			
			GraphElement parent = containsEdge.getNode(EdgeDirection.FROM);
			if(parent.taggedWith(containingTag)){
				return parent;
			}
			
			node = parent;
		}
	}
	
}
