Offline test tools (Python 3 + Java 21, Mesa for the OpenGL ones):
  gl_harness.py + run_gl4.py / run_combo.py / run_sky.py   compile and RUN the real shaders on a software OpenGL context
                                                          (xvfb-run python3 run_gl4.py all) and write PNGs to /tmp
  vehicles/PhysicsTest.java     simulates 0-100, braking and turning of every vehicle (pure Java, no Minecraft needed)
  vehicles/GeometryDump.java + preview.py   dump the vehicle models to JSON and draw previews
  gen_assets.py                 regenerates every texture / model / recipe / language file
