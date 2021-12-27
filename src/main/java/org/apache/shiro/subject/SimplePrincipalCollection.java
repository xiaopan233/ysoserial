package org.apache.shiro.subject;

import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class SimplePrincipalCollection implements MutablePrincipalCollection{
    private static final long serialVersionUID = -6305224034025797558L;
    private Map<String, Set> realmPrincipals;
    private transient String cachedToString;

    @Override
    public Iterator iterator() {
        return null;
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeBoolean(true);
        HashMap<String, Set> stringSetHashMap = new HashMap<String, Set>();
        out.writeObject(stringSetHashMap);
    }
}
