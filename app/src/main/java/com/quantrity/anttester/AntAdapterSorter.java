package com.quantrity.anttester;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class AntAdapterSorter {
    private AntAdapterSorter() { }

    static List<AntAdapterSnapshot> sort(Collection<AntAdapterSnapshot> input) {
        ArrayList<AntAdapterSnapshot> result = new ArrayList<AntAdapterSnapshot>(input == null
                ? Collections.<AntAdapterSnapshot>emptyList() : input);
        Collections.sort(result, new Comparator<AntAdapterSnapshot>() {
            @Override public int compare(AntAdapterSnapshot left, AntAdapterSnapshot right) {
                if (left.builtIn != right.builtIn) return left.builtIn ? -1 : 1;
                int value = left.displayName.compareToIgnoreCase(right.displayName);
                return value != 0 ? value : left.adapterKey.compareToIgnoreCase(right.adapterKey);
            }
        });
        return result;
    }
}
