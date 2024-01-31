import java.util.Map;
import javafx.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap; 
import visitor.GJDepthFirst;
import syntaxtree.*;

public class FirstVisitor extends GJDepthFirst< String, ClassData>{

    /* use a map list storing (class_name, meta_data) pairs, also a list for each method holding its variables */
    public Map <String, ClassData> classes;
    private List <String> decls;
    private Integer row, nextVar, nextMethod;

    /* Constructor: initialize the map and row number */ 
    public FirstVisitor(){
        this.classes = new LinkedHashMap<String, ClassData>();
        this.row = new Integer(1);
        this.nextVar = new Integer(0);
        this.nextMethod = new Integer(0);
        this.decls = new ArrayList<String>();
    }

    /*  Goal
     f0 -> MainClass()
     f1 -> ( TypeDeclaration() )*
     f2 -> <EOF>
    */
    public String visit(Goal node, ClassData data) throws RuntimeException{
        node.f0.accept(this, null);

        // visit all user-defined classes 
        for(int i = 0; i < node.f1.size(); i++)
            node.f1.elementAt(i).accept(this, null);
        return null; 
    }

    /*  MainClass
        class f1 -> Identifier(){
            public static void main(String[] f11 -> Identifier()){ 
                f14 -> ( VarDeclaration() )*
                f15 -> ( Statement() )* 
        } 
    */
    public String visit(MainClass node, ClassData data) throws RuntimeException{
        /* get name of main method */
        String main = node.f1.accept(this, null);

        /* create a ClassData object and pass it down to the variables and methods declaration sections */
        ClassData cd = new ClassData(null);

        /* initialize offsets */
        this.nextVar = this.nextMethod = 0;

        /* pass ClassData to each variable of the variables section */
        for(int i = 0; i < node.f14.size(); i++)
            node.f14.elementAt(i).accept(this, cd);

        /* pass ClassData to each statement of the statements section */
        for(int i = 0; i < node.f15.size(); i++)
            node.f15.elementAt(i).accept(this, cd);
        
        this.classes.put(main, cd);
        return null;
    }
    /* TypeDeclaration
    f0 -> ClassDeclaration() | ClassExtendsDeclaration() */
    public String visit(TypeDeclaration node, ClassData data) throws RuntimeException{

        /* initialize offsets for each new class*/
        this.nextVar = this.nextMethod = 0;
        node.f0.accept(this, null);
        return null;
    }

    /* ClassDeclaration
    class f1 -> Identifier(){
        f3 -> ( VarDeclaration() )*
        f4 -> ( MethodDeclaration() )*
    }
    */
    public String visit(ClassDeclaration node, ClassData data) throws RuntimeException{
        String id = node.f1.accept(this, null);

        /* do not allow class re-declaration */
        if(this.classes.containsKey(id))
            throw new SemError("class '" + id + "' has been declared already", this.row);

        ClassData cd = new ClassData(null);

        /* pass ClassData to each variable of the variables section */
        for(int i = 0; i < node.f3.size(); i++)
            node.f3.elementAt(i).accept(this, cd);

        /* pass ClassData to each statement of the statements section */
        for(int i = 0; i < node.f4.size(); i++)
            node.f4.elementAt(i).accept(this, cd);
        
        this.classes.put(id, cd);
        return null;
    }

    /*
        class f1 -> Identifier() f2 -> "extends" f3 -> Identifier(){}
            f5 -> ( VarDeclaration() )*
            f6 -> ( MethodDeclaration() )*
        }
    */
    public String visit(ClassExtendsDeclaration node, ClassData data) throws RuntimeException {
    	String id = node.f1.accept(this, null);
        String parent = node.f3.accept(this, null);
        
        /* do not allow class re-declaration */
        if(this.classes.containsKey(id))
            throw new SemError("class '" + id + "' has been declared already", this.row);
    	
    	/* check if parent class has been declared */
    	if (this.classes.containsKey(parent) == false)
    		throw new SemError("class '" + id + "' extends not previously declared class '" + parent +"'", this.row);
        
        /* pass a meta data object down to the declarations sections, derived class inherits all fields and methods */ 
        ClassData cd = new ClassData(parent), cdParent = this.classes.get(parent);
        cd.vars.putAll(cdParent.vars);
        cd.methods.putAll(cdParent.methods);

    	for (int i = 0; i < node.f5.size(); i++)
            node.f5.elementAt(i).accept(this, cd);
            
    	for (int i = 0; i < node.f6.size(); i++)
            node.f6.elementAt(i).accept(this, cd);

        this.classes.put(id, cd);
    	return null;
    }

    /*  VarDeclaration
        f0 -> Type()
        f1 -> Identifier()
    bind each variable name/id to a type*/
    public String visit(VarDeclaration node, ClassData classdata) throws RuntimeException{
        String type = node.f0.accept(this, null);
        String id = node.f1.accept(this, null);

        /* make sure this method does not declare the variable twice*/
        if(classdata == null){
            if(this.decls.contains(id))
                throw new SemError("variable '" + id + "' has already been declared in this method", this.row);
        }
        /* or this class does not already contain a field with the same name*/
        else if(classdata.vars.containsKey(id)){
            System.out.println("contains");
            if(classdata.parentName == null  || !this.classes.get(classdata.parentName).vars.containsKey(id))
                throw new SemError("variable '" + id + "' has already been declared in this class", this.row);
        }

        /* store the variable and calculate the exact memory address for the next one to be stored */
        Pair<String, Integer> pair = new Pair<String, Integer>(type, this.nextVar);
        store(type);
        if(classdata == null)
            this.decls.add(id);

        /* if it is not about a variable declared in a method, but in a class, update lookup Table */
        if(classdata != null)
            classdata.vars.put(id, pair);
        return null;
    }

    /*
        public f1 -> Type() f2 -> Identifier() (f4 -> ( FormalParameterList() )?){
            f7 -> ( VarDeclaration() )*
            f8 -> ( Statement() )*
            return f10 -> Expression();
        }
     */
    public String visit(MethodDeclaration node, ClassData classdata) throws RuntimeException {
        String type = node.f1.accept(this, null);
        String id = node.f2.accept(this, null);

        /* make sure this class does not already contain a not inherited member method with the same name */
        if(classdata.methods.containsKey(id)){
            if(classdata.parentName == null  || !this.classes.get(classdata.parentName).methods.containsKey(id))
                throw new SemError("member method '" + id + "' has already been declared in this class", this.row);
        }

    	
        /* get argument list, if it exists */
        ArrayList<String> args = null;
        String [] arglist;
    	if (node.f4.present()){
            args = new ArrayList<String>();
            arglist = node.f4.accept(this, null).split(",");
            for(String arg : arglist)
                args.add(arg);
        }
    	
    	/* check whether the method overrides a super class method */	
        String parent = classdata.parentName;
        ArrayList<String> parentArgs;
        ClassData parentClassData = this.classes.get(parent);
        Triplet<String, Integer, ArrayList<String>> parentData = parentClassData != null && parentClassData.methods.containsKey(id) ? parentClassData.methods.get(id) : null;
        
        /* number of args, if any, should match */
        if(parentData != null){
            parentArgs = parentData.getThird();
            if((args != null && parentArgs != null && (!parentData.getFirst().equals(type) || !args.equals(parentArgs))) || ((args == null && parentArgs != null) || (parentArgs == null && args != null)))
                throw new SemError("child method '" + id + "' does not match super class prototype", this.row);
        }
        

        /* check whether any variable is re-declared*/
        node.f7.accept(this, null);

        /* store a pointer to the method and calculate the exact memory address for the next one to be stored */
        Triplet<String, Integer, ArrayList<String>> data = new Triplet<String, Integer, ArrayList<String>>(type, this.nextMethod, args);
        this.nextMethod += 8;
        classdata.methods.put(id, data);
        this.decls.clear();
        return null;
    }

    /* FormalParameterList: f0 -> FormalParameter() f1 -> FormalParameterTail() */
    public String visit(FormalParameterList node, ClassData classdata) throws RuntimeException {
    	String head = node.f0.accept(this, null);
        String tail = node.f1.accept(this, null);
    	return head + tail;
    }

    /* FormalParameter f0 -> Type() f1 -> Identifier() Returns just the parameter type as a String*/
    public String visit(FormalParameter node, ClassData classdata) throws RuntimeException {
        String type = node.f0.accept(this, null), id = node.f1.accept(this, null);
        this.decls.add(id);
        return type;
    }

    /* FormalParameterTail f0 -> ( FormalParameterTerm)* */
    public String visit(FormalParameterTail node, ClassData classdata) throws RuntimeException {
    	String retval = "";
    	for (int i = 0; i < node.f0.size(); i++)
    		retval += node.f0.elementAt(i).accept(this, null);
    	return retval;
    }

    /* FormalParameterTerm: ,f1 -> FormalParameter */
    public String visit(FormalParameterTerm node, ClassData classdata) throws RuntimeException{
        return "," + node.f1.accept(this,null);
    }

    /* Type: f0 -> ArrayType() | BooleanType() | IntegerType() | Identifier() */
    public String visit(Type node, ClassData classdata){
        return node.f0.accept(this, null);
    }

    /* Return each primitive type of MiniJava(int, int [] and boolean) as a String */ 
    public String visit(ArrayType node, ClassData data){
        return "array";
    }

    public String visit(BooleanType node, ClassData data){
        return "boolean";
    }

    public String visit(IntegerType node, ClassData data){
        return "integer";
    }
    
    /* Identifier f0: return the id as a string and also set the (row, col) pair*/
    public String visit(Identifier node, ClassData data){
        this.row = node.f0.beginLine;
        return node.f0.toString();
    }
    
    /* store a variable in memory and move a pointer(this.nextVar) to the next address available */
    public void store(String type){
        if("integer".equals(type))
            this.nextVar += 4;
        else if("boolean".equals(type))
            this.nextVar += 1;
        else
            this.nextVar += 8;
    }

}
