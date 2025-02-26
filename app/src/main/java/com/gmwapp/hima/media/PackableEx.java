package com.gmwapp.hima.media;

public interface PackableEx extends Packable {
    void unmarshal(ByteBuf in);
}
