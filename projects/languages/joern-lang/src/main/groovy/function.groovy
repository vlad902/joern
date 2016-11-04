
addStep("functionToLocationStr", {
  delegate.as('func')
  .functionToFile().as('file')
  .select('func', 'file')
  .map { it.get()['file'].values('code').next() + ':' +  it.get()['func'].values('location').next() }
})

/**
   (Optimized) match-traversals for functions.
*/

addStep("functionToAST", {
	delegate.out(FUNCTION_TO_AST_EDGE)
})


addStep("functionToCFG", {
	delegate.out(FUNCTION_TO_CFG_EDGE)
})


addStep("functionToASTNodes", {
	delegate.functionToAST().astNodes()
})

/**
	functionToStatements, implemented as a CFG traversal. This does not benefit from
 	potential index lookups, but is lazy, Order is preserved in a depth first traversal.
 */
addStep('functionToStatementsTraverse', {
	delegate
		.functionToCFG()
		.emit()
		.repeat(
			__
			.out(CFG_EDGE)
			.simplePath()
		)
		.dedup()
})

/**
	functionToStatements, implemented with a potential lookup. This may benefit from potential
	index lookups, but is no longer lazy, Order is not preserved.
 */
addStep('functionToStatementsLookup', {
	fids = delegate
			.functionToCFG()
			.values('functionId').collect()
	g.V()
			.has('functionId',P.within(fids))
			.has('isCFGNode','True')
})

addStep('functionToStatements', {
	delegate.functionToStatementsTraverse()
})

GraphTraversal.metaClass.functionsToASTNodesOfType = { def args; def type = args[0];
	delegate.transform{ queryNodeIndex('functionId:' + it.id + " AND $NODE_TYPE:$type") }
	 .scatter()
}

GraphTraversal.metaClass.functionToFile = {
	delegate.in(FILE_TO_FUNCTION_EDGE)
}

/**
 * For a function node, get callers using `name` property.
 **/

GraphTraversal.metaClass.functionToCallers = {
	_().transform{

		funcName = it.name
		funcName = funcName.split(' ')[-1].trim()
	   	funcName = funcName.replace('*', '')

		getCallsTo(funcName)
	}.scatter()
}

addStep('pp', { verbose=false ->
	delegate.map({
		result = ""
		switch(it.get().class) {
		case com.thinkaurelius.titan.graphdb.vertices.CacheVertex:
			result = String.format("vertex id: %s\t%s", it.get().id().toString(),
				it.get().properties().toList().stream()
					.filter({ prop -> prop.value() != "" })
					.sorted(Comparator.comparing({ prop -> prop.type.toString() }))
					.map({ prop -> prop.type.toString() + ": " + prop.value() })
					.toArray().toString())
			if (verbose && it.get().keys().contains("functionId")) {
				def function = g.V().has("_key", it.get().value("functionId"))
				def statement = g.V(it.get()).statements()[0]
				def location = (statement == null ? null : statement.values("location")[0])
				result += String.format("\n  in function %s in file %s at line %s\n",
					function.clone()[0].value('code'),
					function.in('IS_FILE_OF')[0].value('code'),
					(location == null ? "?" : location.split(":")[0]))
			}
			break

		case com.thinkaurelius.titan.graphdb.relations.CacheEdge:
			result = String.format("vertex %s --> vertex %s  edge: %s  ",
				it.get().getVertex(0).id().toString(),
				it.get().getVertex(1).id().toString(),
				it.get().getType().toString())
			
			it.get().properties().forEachRemaining { result += String.format("%s: %s", it.key, it.value) }
			break
			
		default:
			result = String.format("%s [%s]", it.get().toString(), it.get().class.toString())
			break
		}

		result
	})
})
