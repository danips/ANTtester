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
                int kind = left.kind.compareTo(right.kind);
                if (kind != 0) return kind;
                int key = left.adapterKey.compareToIgnoreCase(right.adapterKey);
                if (key != 0) return key;
                return left.firmwareVersion.compareTo(right.firmwareVersion);
            }
        });
        return result;
    }
}
