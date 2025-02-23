/**
 * Copyright (c) 2015-2021, Michael Yang 杨福海 (fuhai999@gmail.com).
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.jboot.web.attachment;

import com.jfinal.log.Log;
import com.jfinal.render.IRenderFactory;
import com.jfinal.render.Render;
import com.jfinal.render.RenderManager;
import io.jboot.components.mq.Jbootmq;
import io.jboot.utils.StrUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author michael yang (fuhai999@gmail.com)
 */
public class AttachmentManager {

    private static final Log LOG = Log.getLog(AttachmentManager.class);


    private static Map<String, AttachmentManager> managers = new HashMap<>();

    public static AttachmentManager me() {
        return use("default");
    }

    public static AttachmentManager use(String name) {
        AttachmentManager manager = managers.get(name);
        if (manager == null) {
            synchronized (AttachmentManager.class) {
                if (manager == null) {
                    manager = new AttachmentManager(name);
                    managers.put(name, manager);
                }
            }
        }
        return manager;
    }

    private AttachmentManager(String name) {
        this.name = name;
    }

    /**
     * 默认的 附件容器
     */
    private LocalAttachmentContainer localContainer = new LocalAttachmentContainer();

    /**
     * 其他附件容器
     */
    private List<AttachmentContainer> containers = new CopyOnWriteArrayList<>();

    /**
     * 自定义文件渲染器
     */
    private IRenderFactory renderFactory = RenderManager.me().getRenderFactory();

    /**
     * manager  的名称
     */
    private final String name;

    /**
     * 文件同步的 MQ
     */
    private Jbootmq deleteMq;

    /**
     * MQ 发送消息的 Channel
     */
    private String deleteMqChannel = "attachmentDelete";

    /**
     * 节点消息ID
     */
    private String deleteMqActionId = StrUtil.uuid();


    public String getName() {
        return name;
    }

    public Jbootmq getDeleteMq() {
        return deleteMq;
    }

    public void setDeleteMq(Jbootmq deleteMq) {
        this.deleteMq = deleteMq;
        this.deleteMq.addMessageListener(localContainer, deleteMqChannel);
    }

    public String getDeleteMqChannel() {
        return deleteMqChannel;
    }

    public void setDeleteMqChannel(String deleteMqChannel) {
        this.deleteMqChannel = deleteMqChannel;
    }

    public String getDeleteMqActionId() {
        return deleteMqActionId;
    }

    public void setDeleteMqActionId(String deleteMqActionId) {
        this.deleteMqActionId = deleteMqActionId;
    }

    public IRenderFactory getRenderFactory() {
        return renderFactory;
    }

    public void setRenderFactory(IRenderFactory renderFactory) {
        this.renderFactory = renderFactory;
    }


    public LocalAttachmentContainer getLocalContainer() {
        return localContainer;
    }

    public void setLocalContainer(LocalAttachmentContainer localContainer) {
        this.localContainer = localContainer;
    }

    public void addContainer(AttachmentContainer container) {
        containers.add(container);
    }

    public void setContainers(List<AttachmentContainer> containers) {
        this.containers = containers;
    }


    public List<AttachmentContainer> getContainers() {
        return containers;
    }


    /**
     * 保存文件
     *
     * @param file
     * @return 返回文件的相对路径
     */
    public String saveFile(File file) {
        //优先从 默认的 container 去保存文件
        String relativePath = localContainer.saveFile(file);
        File defaultContainerFile = localContainer.getFile(relativePath);

        for (AttachmentContainer container : containers) {
            try {
                if (container != localContainer) {
                    container.saveFile(defaultContainerFile);
                }
            } catch (Exception ex) {
                LOG.error("Save file error in container :" + container, ex);
            }
        }
        return relativePath.replace("\\", "/");
    }


    /**
     * 保存文件
     *
     * @param file
     * @return 返回文件的相对路径
     */
    public String saveFile(File file, String toRelativePath) {
        //优先从 默认的 container 去保存文件
        String relativePath = localContainer.saveFile(file, toRelativePath);
        File defaultContainerFile = localContainer.getFile(relativePath);

        for (AttachmentContainer container : containers) {
            try {
                if (container != localContainer) {
                    container.saveFile(defaultContainerFile, toRelativePath);
                }
            } catch (Exception ex) {
                LOG.error("Save file error in container :" + container, ex);
            }
        }
        return relativePath.replace("\\", "/");
    }

    /**
     * 保存文件
     *
     * @param inputStream
     * @return
     */
    public String saveFile(InputStream inputStream, String toRelativePath) {
        //优先从 默认的 container 去保存文件
        String relativePath = localContainer.saveFile(inputStream, toRelativePath);
        File defaultContainerFile = localContainer.getFile(relativePath);

        for (AttachmentContainer container : containers) {
            try {
                if (container != localContainer) {
                    container.saveFile(defaultContainerFile, toRelativePath);
                }
            } catch (Exception ex) {
                LOG.error("Save file error in container :" + container, ex);
            }
        }
        return relativePath.replace("\\", "/");
    }


    /**
     * 删除文件
     *
     * @param relativePath
     * @return
     */
    public boolean deleteFile(String relativePath) {
        for (AttachmentContainer container : containers) {
            try {
                container.deleteFile(relativePath);
            } catch (Exception ex) {
                LOG.error("Delete file error in container :" + container, ex);
            }
        }
        boolean success = localContainer.deleteFile(relativePath);

        //删除，同步到其他 Server
        if (success && this.deleteMq != null) {
            deleteMq.publish(new AttachmentDeleteAction(deleteMqActionId, relativePath), deleteMqChannel);
        }

        return success;
    }

    /**
     * 通过相对路径获取文件
     *
     * @param relativePath
     * @return
     */
    public File getFile(String relativePath) {

        //优先从 默认的 container 去获取
        File file = localContainer.getFile(relativePath);
        if (file != null && file.exists()) {
            return file;
        }

        for (AttachmentContainer container : containers) {
            try {
                file = container.getFile(relativePath);
                if (file != null && file.exists()) {
                    return file;
                }
            } catch (Exception ex) {
                LOG.error("Get file error in container :" + container, ex);
            }
        }
        return null;
    }

    /**
     * 通过一个文件，获取其相对路径
     *
     * @param file
     * @return
     */
    public String getRelativePath(File file) {
        String relativePath = localContainer.getRelativePath(file);
        return relativePath != null ? relativePath.replace("\\", "/") : null;
    }


    /**
     * 创建一个新的文件
     * 使用创建一般是创建一个空的文件，然后由外部逻辑进行写入
     *
     * @param suffix
     * @return
     */
    public File createNewFile(String suffix) {
        return getLocalContainer().creatNewFile(suffix);
    }


    /**
     * 渲染文件到浏览器
     *
     * @param target
     * @param request
     * @param response
     * @return true 渲染成功，false 不进行渲染
     */
    public boolean renderFile(String target, HttpServletRequest request, HttpServletResponse response) {
        if (StrUtil.isNotBlank(localContainer.getTargetPrefix())
                && target.startsWith(localContainer.getTargetPrefix())
                && target.lastIndexOf('.') != -1) {
            Render render = getFileRender(getFile(target));
            render.setContext(request, response).render();
            return true;
        } else {
            return false;
        }
    }

    private Render getFileRender(File file) {
        return file == null || !file.isFile()
                ? renderFactory.getErrorRender(404)
                : renderFactory.getFileRender(file);
    }


}
