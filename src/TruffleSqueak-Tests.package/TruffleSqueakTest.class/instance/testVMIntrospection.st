testing
testVMIntrospection
	| testObject metaObject markingFlag |
	Polyglot isPolyglotEvalAllowed ifFalse: [ ^self ].

	testObject := SmalltalkImage vmObject.
	"Every Java object understands hashCode"
	self assert: (Interop fitsInLong: testObject hashCode).
	"Ensure that class has a layout"
	self assert: testObject hasLayout.
	"Traverse class and unwrap object"
	self assert: 'Object' equals: testObject getSuperclass toString asString.
	"Allocate Object[]"
	self assert: 10 equals: (testObject getOtherPointers getClass new: 10) size.
	"Test inst vars"
	self assert: (testObject allInstVarNames includesAllOf: #('squeakHash' 'squeakClass' 'superclass' 'methodDict')).
	self deny: (testObject allInstVarNames includesAnyOf: #('clone' 'become' 'finalize' 'object0')).
	"Read and write inst vars"
	markingFlag := testObject markingFlag.
	self assert: (testObject markingFlag: markingFlag not) = markingFlag not.
	self assert: testObject markingFlag = markingFlag not.
	testObject markingFlag: markingFlag.
	
	"Meta Object API"
	self assert: (Interop hasMetaObject: testObject).
	metaObject := Interop getMetaObject: testObject..
	self assert: (Interop isMetaObject: metaObject).
	self assert: 'ClassObject' equals: (Interop getMetaSimpleName: metaObject) asString.	
	self assert: (Interop isMetaInstance: metaObject instance: testObject).	