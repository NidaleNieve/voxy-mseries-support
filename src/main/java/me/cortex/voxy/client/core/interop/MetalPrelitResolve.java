package me.cortex.voxy.client.core.interop;

import me.cortex.voxy.client.core.gl.shader.ShaderLoader;
import me.cortex.voxy.client.core.gpu.*;
import me.cortex.voxy.common.util.ResourceScope;
import static org.lwjgl.opengl.GL11C.GL_RGBA8;

/** Blends the nearest prelit translucent surface once over the opaque bridge. */
public final class MetalPrelitResolve implements AutoCloseable {
    private final RenderBackend backend;
    private final IGpuPipeline pipeline;
    private final ResourceScope resources = new ResourceScope();

    public MetalPrelitResolve(RenderBackend backend) {
        this.backend = backend;
        try {
            this.pipeline = resources.own(backend.createGraphicsPipeline(new GraphicsPipelineDesc(
                    ShaderLoader.parse("voxy:post/fullscreen2.vert"),
                    ShaderLoader.parse("voxy:post/prelit_resolve.frag"), java.util.Map.of(),
                    null, null, null, null, GL_RGBA8, VertexLayout.EMPTY,
                    new PipelineState(PipelineState.DepthState.DISABLED, PipelineState.BlendState.ALPHA,
                            PipelineState.RasterState.NO_CULL), "MetalPrelitResolve")), IGpuPipeline::close);
        } catch (RuntimeException | Error error) {
            resources.rollback(error);
            throw error;
        }
    }

    public void render(IGpuTexture destination, IGpuTexture layer, int width, int height) {
        var pass = RenderPassDesc.builder(width, height)
                .addColorAttachment(destination, 0, RenderPassDesc.LoadAction.LOAD,
                        RenderPassDesc.StoreAction.STORE, 0, 0, 0, 0).build();
        try (var encoder = backend.beginRenderPass(pass)) {
            encoder.setPipeline(pipeline);
            encoder.setTexture(0, layer);
            encoder.setViewport(0, 0, width, height, 0, 1);
            encoder.draw(RenderEncoder.PRIMITIVE_TRIANGLE_STRIP, 0, 4, 1, 0);
        }
    }

    @Override public void close() { resources.close(); }
}
