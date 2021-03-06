package com.github.t1.deployer.app;

import com.github.t1.deployer.model.Config;
import com.github.t1.deployer.tools.*;
import lombok.extern.slf4j.Slf4j;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;

@Path("/ciphers")
@Stateless
@Slf4j
public class CipherBoundary {
    @Inject
    CipherFacade cipher;

    @Inject @Config("key-store")
    KeyStoreConfig keyStore;

    @POST
    @Path("/encrypt")
    public String encrypt(String body) {
        return cipher.encrypt(body, keyStore);
    }
}
