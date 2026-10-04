# PR #22 teleport and snow/ice investigation

## Scope

Review discussion: https://github.com/srjefers/voxy-mseries-support/pull/22#issuecomment-5976339562

The reviewer reports that previously generated LODs change appearance after
walking to generate terrain and then teleporting nearby. The report applies with
and without `-Dvoxy.bslCompatibility=true`. The supplied screenshots show snow,
ice, and spyglass magnification. Their displayed seed is
`-4124916493478848877`, and one displayed destination is
`-1335.5, 165, -1788.5`. The original position, facing, settings, shader pack,
and exact teleport sequence are not established by those images.

This investigation used commands and numeric CPU/GL/Metal assertions. Minecraft
was not launched and the seed was not generated. The screenshot's exact gameplay
artifact has not been reproduced. No production fix, deployment, or PR update
is included here.

## Revision comparison

The public PR head was `fed768e7701511bc0e118d2c0fc95f302dba102a`.
The locally merged architecture revision was
`b10ecdbc505d107d0729253b532f9e3bc8802881`.

| Check | PR head | Local architecture revision |
| --- | --- | --- |
| Existing aggregate CPU and native verification | 30 tasks passed | 38 tasks passed |
| Camera-jump traversal fixture | Passed | Passed |
| Camera-jump fixture with explicit BSL compatibility property | Passed | Passed |
| Projection including 1 and 7 degree FOV, resize and camera transforms | 20 cases passed | 20 cases passed |
| Partial-height bake inset | Failed for 1/8 and 1/2 height | Same failures |
| Overlapping bake planes in reverse submission order | Failed | Same failure |

Aggregate verification included the supplied Complementary Unbound r5.9.3
programs, not an unidentified pack from the screenshots. BSL-flag traversal
checks do not establish correctness of an actual BSL shader pack. The retained
ARM64 native library was used; the build reports unavailable CMake and does not
rebuild native source.

## Reproduced bakery defects

`MetalBakeDepthRegressionTest` supplies known horizontal planes to the real
Metal bake renderer, using the production UP-face view matrix and the production
Metal projection. The resulting pixels are consumed through
`TextureUtils.computeDepth`, as production classification consumes them.

- Height 0.125: expected inset 0.875, observed 0.0.
- Height 0.5: expected inset 0.5, observed 0.0.
- Height 1.0: expected and observed inset 0.0 (control passes).
- Two overlapping planes: the nearer white plane should remain visible. When
  the lower dark plane is submitted last, its color wins instead. Reversing
  submission order gives the correct white result.

Both cases also validate that the fixture actually paints covered pixels. These
are failures of the production bakery, not compilation or context setup failures.

`MetalViewCapture.emitToStream` supplies coverage/tint but no depth bits to the
existing packed metadata. `MetalBudgetBufferRenderer` disables depth testing
and culling. Consequently, non-full-block alignment is lost and overlapping
quads are sensitive to submission order. The existing fluid-height workaround
covers water, not every partial-height block.

These defects plausibly affect snow layers and other partial models. They do
not prove that teleporting corrupts caches, explain every translucent-ice
artifact, or establish the complete cause of the screenshots. Normal coarse
LOD approximation must be separated from erroneous model reconstruction.

## What the passing tests establish

The added camera-jump fixture reuses GPU node tables over 960 frames and 1,120
real Metal dispatches. It alternates visible and culled camera positions, coarse
and fine selection, and available/unavailable descendants. It checks retained
mesh and position identity, correct child selection, parent fallback, request
counts, and current visibility stamps. Cases include negative and large signed
world coordinates. Camera inputs are controlled scenes, not world generation.

The existing lifecycle tests exercise worker-failure propagation and termination,
constructor rollback, publication ordering, cleanup faults, and resource
replacement. They are not a long-running gameplay memory or process profile;
no claim of zero leaks or improved performance follows from them.

## Reproduction commands

Inside this isolated test checkout:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
./gradlew --offline testMetalTraversal testProjection --console=plain
JAVA_TOOL_OPTIONS=-Dvoxy.bslCompatibility=true \
  ./gradlew --offline testMetalTraversal testProjection --console=plain
./gradlew --offline testMetalBakeDepth --console=plain
```

The last command deliberately fails until production bake depth and visibility
are corrected. It is separate from aggregate tasks to preserve the distinction
between existing checks and newly exposed failures. Test code stays in the test
source set. Existing native and full-pack checks can be run with:

```sh
./gradlew --offline verifyCpu verifyMetal \
  -PcomplementaryPack=/path/to/ComplementaryUnbound_r5.9.3.zip --continue --console=plain
```

## Next correction and acceptance

1. Keep the failing plane-depth and submission-order tests as the regression
   criteria before implementing changes.
2. Adapt the bakery to capture actual nearest-visible geometry depth and encode
   it into the existing metadata contract, including inversion of the Metal
   projection's compressed Z mapping. Preserve genuine coverage and tint bits.
3. Reconcile face culling, opaque depth selection, and translucent layering with
   the GL bakery's semantics. Do not replace them with opaque fills or assume
   last-submitted surfaces are visible. Test all six views, empty faces, partial
   heights, two-sided plants, ice/glass, and water.
4. Verify existing cutout/tint/mip, terrain, water and Complementary regressions.
   Keep saved-world formats and caches unchanged.
5. Test model mip reconstruction and ordinary/spyglass selection separately from
   teleport lifecycle. If the gameplay defect remains, capture matched data,
   mesh publication, LOD selection, model depth, and bridge pixels around the
   exact teleport sequence in a disposable world.
6. Run representative reload/join/teleport memory and thread measurements before
   claiming long-running resource correctness.

No render-distance changes, arbitrary waits, forced colors, cache deletion,
or speculative production modifications were made during this investigation.
