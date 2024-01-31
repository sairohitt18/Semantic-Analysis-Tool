import javafx.util.Pair;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap; 

/* for each class, store some meta data */
public class ClassData{
    String parentName;
    Map <String, Pair<String, Integer>> vars;       // records of form: (variable_name, (type, offset))
    Map <String, Triplet<String, Integer, ArrayList<String>>> methods;    // records of form: (function_name, (return_type, offset, arglist))

    public ClassData(String parent){
        this.parentName = parent;
        this.vars = new LinkedHashMap<String, Pair<String, Integer>>();
        this.methods = new LinkedHashMap<String, Triplet<String, Integer, ArrayList<String>>>();
    }
}

