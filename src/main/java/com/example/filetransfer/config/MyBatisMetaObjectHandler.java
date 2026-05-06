package com.example.filetransfer.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        if (metaObject.hasSetter("version")) {
            strictInsertFill(metaObject, "version", Long.class, 0L);
        }
        if (metaObject.hasSetter("createdAt")) {
            strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        }
        if (metaObject.hasSetter("updatedAt")) {
            strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (metaObject.hasSetter("updatedAt")) {
            strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
        }
    }
}
