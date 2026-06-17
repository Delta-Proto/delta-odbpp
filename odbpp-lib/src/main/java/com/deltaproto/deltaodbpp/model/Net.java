package com.deltaproto.deltaodbpp.model;

import lombok.Data;
import java.util.List;

@Data
public class Net {
    private String name;
    private int index;
    private List<PinConnection> pinConnections;
}
