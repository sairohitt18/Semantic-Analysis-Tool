import java.util.Map;
import javafx.util.Pair;
import java.util.ArrayList;

import visitor.GJNoArguDepthFirst;
import syntaxtree.*;

public class SecondVisitor extends GJNoArguDepthFirst<String> {
    private Map<String, ClassData> table;
    private ArrayList<Pair<String, String>> scope;
    private ArrayList<String> args;
    private int index;
    private Integer row;
    private String className;


    /* add all inherited fields in scope of a derived class */
    private void addInScope(){
        Map<String, Pair<String, Integer>> map = this.table.get(this.className).vars;
        for(String key: map.keySet())
            this.scope.add(new Pair<String, String>(key, map.get(key).getKey()));
    }

    /* check whether a data type is a primitive or a user-defined class */
    private boolean isPrimitive(String type){
        return type.equals("integer") || type.equals("array") || type.equals("boolean");
    }
    /* recursively inspect whether a class is equivalent or sub-type of another */
    private boolean isSubType(String childType, String parentType){
        String subType = childType;
        ClassData cd = this.table.get(subType);

        /* if it is a primitive type it should be identical */
        if(isPrimitive(childType))
            return childType.equals(parentType);

        /* else if it is user-defined it might can also be a sub-type */
        do {
            if(subType.equals(parentType))
                return true;
            subType = cd.parentName;
            cd = this.table.get(subType);       
        } while (cd != null);
        return false;
    }

    /* return the last declared type for a given variable name */
    private String lastDeclarationOf(ArrayList<Pair<String, String>> list, String varName){
        for(int i = list.size()-1; i >= 0; i--){
            if(list.get(i).getKey().equals(varName))
                return list.get(i).getValue();  
        }
        return null;
    }

    /* constructor */
	public SecondVisitor(Map<String, ClassData> table) {
		this.table = table;
        this.row = new Integer(1);
        this.scope = new ArrayList<Pair<String, String>>();
        this.args = null;
	}

    /*  Goal
     f0 -> MainClass()
     f1 -> ( TypeDeclaration() )*
     f2 -> <EOF>
     Visit all methods of the input file
    */
   	public String visit(Goal node) throws RuntimeException {
        node.f0.accept(this);
           
   		for (int i = 0; i < node.f1.size(); i++)
   			node.f1.elementAt(i).accept(this);

   		return null;
   	} 

    /*  MainClass
        class f1 -> Identifier(){
            public static void main(String[] f11 -> Identifier()){ 
                f14 -> ( VarDeclaration() )*
                f15 -> ( Statement() )* 
        } 
    */
   	public String visit(MainClass node) throws RuntimeException {
        this.className = node.f1.accept(this);
   		
   		for (int i = 0; i < node.f14.size(); i++)
 		   node.f14.elementAt(i).accept(this);

   		for (int i = 0; i < node.f15.size(); i++)
               node.f15.elementAt(i).accept(this);

        /* scope is cleared after type checking main class */ 
        this.scope.clear();
   		return null;
   	}

    /*  Type Declaration
        f0 -> ClassDeclaration()    |   ClassExtendsDeclaration() */
   	public String visit(TypeDeclaration node) throws RuntimeException {
        node.f0.accept(this);

        /* scope is cleared after type checking any class */ 
        this.scope.clear();
   		return null;
   	}

    /* ClassDeclaration
    class f1 -> Identifier(){
        f3 -> ( VarDeclaration() )*
        f4 -> ( MethodDeclaration() )*
    }
    */
    public String visit(ClassDeclaration node) throws RuntimeException {
        /* pass name of the class to child nodes */
        this.className = node.f1.accept(this);

        for (int i = 0; i < node.f3.size(); i++)
            node.f3.elementAt(i).accept(this);

        for (int i = 0; i < node.f4.size(); i++)
            node.f4.elementAt(i).accept(this);
        return null;
    }

    /*
        class f1 -> Identifier() f2 -> "extends" f3 -> Identifier(){}
            f5 -> ( VarDeclaration() )*
            f6 -> ( MethodDeclaration() )*
        }
    */
    public String visit(ClassExtendsDeclaration node) throws RuntimeException {
        /* pass name of the class to child nodes */
        this.className = node.f1.accept(this);
        addInScope();
       
        for (int i = 0; i < node.f5.size(); i++)
            node.f5.elementAt(i).accept(this);

        for (int i = 0; i < node.f6.size(); i++)
            node.f6.elementAt(i).accept(this);
        return null;
    }

    /*VarDeclaration
    * f0 -> Type()
    * f1 -> Identifier()
    */
    public String visit(VarDeclaration node) throws RuntimeException {
    
        /* make sure that the type of the variable is defined */
        String varType = node.f0.accept(this);
        if (varType.equals("integer") == false && varType.equals("array") == false && varType.equals("boolean") == false && !this.table.containsKey(varType))
            throw new SemError("undefined type '" + varType + "'", this.row);    
        
        /* Add variable to scope list */
        this.scope.add(new Pair<String, String>(node.f1.accept(this), varType));	
        return null;
    }

    /*  MethodDeclaration
        public f1 -> Type() f2 -> Identifier() (f4 -> ( FormalParameterList() )?){
            f7 -> ( VarDeclaration() )*
            f8 -> ( Statement() )*
            return f10 -> Expression();
        }
     */
   	public String visit(MethodDeclaration node) throws RuntimeException {
	   
        /* make sure all types used(return value and parameters) are of a at some point defined object in this file */
        String returnType = node.f1.accept(this);	   
        if (returnType.equals("integer") == false && returnType.equals("array") == false && returnType.equals("boolean") == false && !this.table.containsKey(returnType))
            throw new SemError("Undefined return type '" + returnType + "'", this.row);
        if (node.f4.present()) 
            node.f4.accept(this);

        /* check variable declarations */
        int framePointer = this.scope.size();
        for (int i = 0; i < node.f7.size(); i++)
            node.f7.elementAt(i).accept(this);
        
        /* check statements */
        for (int i = 0; i < node.f8.size(); i++)
            node.f8.elementAt(i).accept(this);
        
        /* make sure return type matches the prototype, allow behavioral sub-typing */
        String methodName = node.f2.accept(this), returns = node.f10.accept(this);
        if (returns.equals(returnType) == false && isSubType(returns, returnType) == false)
            throw new SemError("class " + this.className + ": method " + methodName + " should return '" + returnType + "', not '" + returns + "'", this.row);
        
        /* pop all variables pushed in scope from the scope list/stack */
        for(int i = framePointer; i < this.scope.size(); i++)
            this.scope.remove(i); 
        return null;
   	}

    /* FormalParameterList
    * f0 -> FormalParameter()
    * f1 -> FormalParameterTail() */
    public String visit(FormalParameterList node) throws RuntimeException {
        node.f0.accept(this);
        node.f1.accept(this);
        return null;
    }

    /* FormalParameter
    * f0 -> Type()
    * f1 -> Identifier() */
    public String visit(FormalParameter node) throws RuntimeException {
        String varType = node.f0.accept(this), name = node.f1.accept(this);
        if (varType.equals("integer") == false && varType.equals("array") == false && varType.equals("boolean") == false && !this.table.containsKey(varType))
            throw new SemError("undefined type: '" + varType + "'", this.row);

        /* Insert all parameters in scope */
        this.scope.add(new Pair<String, String>(name, varType));
        return null;
    }

    /* FormalParameterTail
    * f0 -> ( FormalParameterTerm() )* */
    public String visit(FormalParameterTail node) throws RuntimeException {
        for (int i = 0; i < node.f0.size(); i++)
            node.f0.elementAt(i).accept(this);
        return null;
    }

    /* FormalParameterTerm 
    * f1 -> FormalParameter() */
    public String visit(FormalParameterTerm node) throws RuntimeException {
        node.f1.accept(this);
        return null;
    }

    /* Type
    * f0 -> ArrayType() | BooleanType() | IntegerType() | Identifier() */
   	public String visit(Type node) throws RuntimeException {
    	return  node.f0.accept(this);
   	}

    /* ArrayType */
   	public String visit(ArrayType node) throws RuntimeException {
      	return "array";
   	}

    /* BooleanType */
   	public String visit(BooleanType node) throws RuntimeException {
     	 return "boolean";
   	}

    /* IntegerType*/
   	public String visit(IntegerType node) throws RuntimeException {
    	return "integer";
   	}

    /* f0 -> Block() | AssignmentStatement() | ArrayAssignmentStatement() | IfStatement() | WhileStatement() | PrintStatement() */
    public String visit(Statement node) throws RuntimeException {
        node.f0.accept(this);
        return null;
    }

    /*  Block: {
            ( Statement() )*
        }
    */
    public String visit(Block node) throws RuntimeException {
        for (int i = 0; i < node.f1.size(); i++)
            node.f1.elementAt(i).accept(this);
        return null;
    }

    /*  Assignment Statement
        f0 -> Identifier() = f2 -> Expression();
    */
    public String visit(AssignmentStatement node) throws RuntimeException {
        String var = node.f0.accept(this), exprType = node.f2.accept(this), hasType = lastDeclarationOf(this.scope, var);
      
        /* make sure the variable on the left of the assignment is declared and get the innermost declaration of its*/
        if (hasType == null)
            throw new SemError("undeclared variable '" + var + "'", this.row); 
       
        /* use recursion to make sure the right hand side expression evaluates to a matching data type, equivalent or sub-type */ 
        if(isSubType(exprType, hasType))
            return null;
        throw new SemError("assigning '" + exprType + "' to a variable declared as '" + hasType + "'", this.row);
    }

    /*  ArrayAssignmentStatement
    *   f0 -> Identifier() [f2 -> Expression()] = f5 -> Expression(); */
    public String visit(ArrayAssignmentStatement node) throws RuntimeException {

        /* Index must be an integer */
        String indexType = node.f2.accept(this);
        if (indexType.equals("integer") == false)
            throw new SemError("invalid index '" + indexType + "'", this.row);
        
        /* make sure to only assign to arrays */
        String arrayID = node.f0.accept(this), type = lastDeclarationOf(this.scope, arrayID);
        if (type == null)
            throw new SemError("'" + arrayID + "' has not been declared", this.row);

        if (type.equals("array") == false)
            throw new SemError("assignment to non-array type", this.row);
        
        /* right side expression should evaluate to 'integer' */
        String exprType = node.f5.accept(this);
        if (exprType.equals("integer") == false)
            throw new SemError("can not convert " + "'" + exprType + "' to  'integer'", this.row);
        
        return null;
    }

    /*IfStatement
    * if( f2 -> Expression())
    *       f4 -> Statement()
    * else
    *       f6 -> Statement()
    */
    public String visit(IfStatement node) throws RuntimeException {

        /* make sure condition evaluates to true or false */
        String condType = node.f2.accept(this);
        if (condType.equals("boolean") == false)
            throw new SemError("condition is of type '" + condType + "'", this.row);
        
        node.f4.accept(this);
        node.f6.accept(this); 
        return null;
    }

    /*WhileStatement
    * while( f2 -> Expression())
    *       f4 -> Statement()
    */
    public String visit(WhileStatement node) throws RuntimeException {

        /* make sure condition evaluates to true or false */
        String condType = node.f2.accept(this);
        if (condType.equals("boolean") == false)
            throw new SemError("condition is of type '" + condType + "'", this.row);
        
        node.f4.accept(this);
        return null;
    }

    /*PrintStatement
      System.out.println( f2 -> Expression() ); 
    */
    public String visit(PrintStatement node) throws RuntimeException {
        String argType = node.f2.accept(this);
        if (argType.equals("integer") == false && argType.equals("boolean") == false)
            throw new SemError("cannot print type '" + argType +"'", this.row);
        return null;
    }

    /*Expression
    * f0 -> AndExpression() | CompareExpression() | PlusExpression() | MinusExpression() 
        | TimesExpression() | ArrayLookup() | ArrayLength() | MessageSend() | Clause()
    */
    public String visit(Expression node) throws RuntimeException {
        return node.f0.accept(this);
    }

    /*AndExpression f0 -> Clause() &&  f2 -> Clause() */
    public String visit(AndExpression node) throws RuntimeException {
        String leftType = node.f0.accept(this), rightType = node.f2.accept(this);
        if (leftType.equals("boolean") == false || rightType.equals("boolean") == false)
            throw new SemError("&& can only be applied to booleans", this.row);
        return "boolean";
    }


    /* Arithmetic Expression Generic Function */
    public String arithmeticExpression(String leftType, String rightType, char exp){
        if (leftType.equals("integer") == false || rightType.equals("integer") == false)
            throw new SemError("arithmetic expression '" + exp + "' can not be applied to types other than 'integer'", this.row);
        return exp == '<' ? "boolean" : rightType;
    }
    /*CompareExpression
    * f0 -> PrimaryExpression() < f2 -> PrimaryExpression() */
    public String visit(CompareExpression node) throws RuntimeException {
        return arithmeticExpression(node.f0.accept(this), node.f2.accept(this), '<');
    }

    /*PlusExpression
    * f0 -> PrimaryExpression() + f2 -> PrimaryExpression() */
    public String visit(PlusExpression node) throws RuntimeException {
        return arithmeticExpression(node.f0.accept(this), node.f2.accept(this), '+');
    }

    /*MinusExpression
    * f0 -> PrimaryExpression() - f2 -> PrimaryExpression() */
    public String visit(MinusExpression node) throws RuntimeException {
        return arithmeticExpression(node.f0.accept(this), node.f2.accept(this), '-');
    }

    /*TimesExpression
    * f0 -> PrimaryExpression() * f2 -> PrimaryExpression() */
    public String visit(TimesExpression node) throws RuntimeException {
        return arithmeticExpression(node.f0.accept(this), node.f2.accept(this), '*');
    }

    /*ArrayLookup
    * f0 -> PrimaryExpression() [f2 -> PrimaryExpression()] */
    public String visit(ArrayLookup node) throws RuntimeException {
        String arrayType = node.f0.accept(this);
        if (arrayType.equals("array") == false)
            throw new SemError("trying to look up non-array type", this.row);
        
        String indexType = node.f2.accept(this);
        if (indexType.equals("integer") == false)
            throw new SemError("array index is not an 'integer'", this.row);      
        return "integer";
    }

    /*ArrayLength
    * f0 -> PrimaryExpression().length */
    public String visit(ArrayLength node) throws RuntimeException {
        String arrayType = node.f0.accept(this);
        if (arrayType.equals("array") == false)
            throw new SemError("length method can only be applied to arrays", this.row);      
        return "integer";
    }

    /*MessageSend
    * f0 -> PrimaryExpression().f2 -> Identifier()(f4 -> ( ExpressionList() )?) */
    public String visit(MessageSend node) throws RuntimeException {
        /* make sure the receiving class exists */
        String className = node.f0.accept(this);
        if (!this.table.containsKey(className) && lastDeclarationOf(this.scope, className) == null)
            throw new SemError("to non-existent class " + className, this.row);
        
        /* recursively check whether this class (re)defines or inherits the given method */
        ClassData classData = this.table.get(className);
        String methodName = node.f2.accept(this), currentClass = className;
        Triplet<String, Integer, ArrayList<String>> methodData = null, last = null;

        do {
            classData = this.table.get(currentClass);
            if(classData != null){
                last = classData.methods.get(methodName);
                if(last != null)
                    methodData = last;
                currentClass = classData.parentName;
            }
        }while(classData != null);

        /* if no method definition was found */
        if(methodData == null)
            throw new SemError("class '" + className + "' does not contain or inherit method '" + methodName + "'", this.row);
        
        /* make sure parameters are of the right type */
        ArrayList<String> temp = this.args;
        if (node.f4.present() == true) {	
            this.args = methodData.getThird(); 
            if(this.args == null || node.f4.accept(this) == null || this.index != this.args.size())
                throw new SemError("function call '" + className + "." + methodName + "' parameters do not match prototype", this.row);
        }
        /* or if there are no parameters given although they should be */
        else if(methodData.getThird() != null)
            throw new SemError("function call '" + className + "." + methodName + "' parameters do not match prototype", this.row);


        
        /* Return return type of method */
        this.args = temp;
        return methodData.getFirst();	   
    }

    /*ExpressionList: f0 -> Expression() f1 -> ExpressionTail()*/
    public String visit(ExpressionList node) throws RuntimeException {

        /* make sure function call matches prototype */
        String argType = node.f0.accept(this), declType = this.args.get(0);
        if(declType == null || isSubType(argType, declType) == false)
            return null;
        this.index = 1;
        return node.f1.accept(this);
    }

    /*ExpressionTail: f0 -> ( ExpressionTerm() )* */																		
    public String visit(ExpressionTail node) throws RuntimeException {

        /* return null if any of the parameters type does not match prototype*/
        for (int i = 0; i < node.f0.size(); i++){
            if(node.f0.elementAt(i).accept(this) == null)
                return null;
        }
        return "ok";
    }

    /*ExpressionTerm: ,f1 -> Expression() */
    public String visit(ExpressionTerm node) throws RuntimeException {

        /* return null if any of the parameters type does not match prototype*/
        String declType = this.args.get(this.index);
        if(declType == null || isSubType(node.f1.accept(this), declType) == false)
            return null;
        this.index++;
        return "ok";
    }

    /*Clause: f0 -> NotExpression() | PrimaryExpression() */
    public String visit(Clause node) throws RuntimeException {
        return node.f0.accept(this);	
    }

    /*PrimaryExpression
    * f0 -> IntegerLiteral() | TrueLiteral() | FalseLiteral() | Identifier() 
    | ThisExpression() | ArrayAllocationExpression() | AllocationExpression() | BracketExpression() */
    public String visit(PrimaryExpression node) throws RuntimeException {
        String var = node.f0.accept(this), type = null;

        /* in case of an identifier, make sure the variable is declared */
        if (node.f0.which == 3){
            type = lastDeclarationOf(this.scope, var);
            if(type == null)
                throw new SemError("variable '" + var + "' has not been declared", this.row);
            else 
                return type;
        }
        return var;
    }

    /*IntegerLiteral
    * f0 -> <INTEGER_LITERAL> */
    public String visit(IntegerLiteral node) throws RuntimeException {
        this.row = node.f0.beginLine;
        return "integer";
    }

    /*TrueLiteral
    * f0 -> "true" */
    public String visit(TrueLiteral node) throws RuntimeException {
        this.row = node.f0.beginLine;
        return "boolean";
    }

    /*FalseLiteral
    * f0 -> "false" */
    public String visit(FalseLiteral node) throws RuntimeException {
        this.row = node.f0.beginLine;
        return "boolean";
    }

    /*Identifier
    * f0 -> <IDENTIFIER>*/
    public String visit(Identifier node) throws RuntimeException {
        this.row = node.f0.beginLine;
        return node.f0.toString();
    }

    /*ThisExpression
    * f0 -> "this" */ 
    public String visit(ThisExpression node) throws RuntimeException {  
        this.row = node.f0.beginLine;
        return this.className;
    }

    /*ArrayAllocationExpression
    * new integer [f3 -> Expression()] */
    public String visit(ArrayAllocationExpression node) throws RuntimeException {
        String index = node.f3.accept(this);      
        if (index.equals("integer") == false){
            throw new SemError("array size is not an 'integer'", this.row);
        }
        return "array";
    }

    /*AllocationExpresion
    * new f1 -> Identifier()() */
    public String visit(AllocationExpression node) throws RuntimeException {
        String className = node.f1.accept(this);
        if (this.table.containsKey(className) == false)
            throw new SemError("undefined type " + "'" + className + "'", this.row);
        return className;
    }

    /*NotExpression
    * !f1 -> Clause() */
    public String visit(NotExpression node) throws RuntimeException {
        String clauseType = node.f1.accept(this);
        if (clauseType.equals("boolean") == false)
            throw new SemError("logical NOT applied on non-boolean expression", this.row);
        return "boolean";
    }

    /*BracketExpression
    * ( f1 -> Expression() )*/
    public String visit(BracketExpression node) throws RuntimeException {
        return node.f1.accept(this);
    }

}
