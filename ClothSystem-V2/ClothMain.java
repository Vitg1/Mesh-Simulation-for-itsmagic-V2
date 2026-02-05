/* @Author Sr Mil Games */

import java.util.*;

// ================= CONFIG — SIMULAÇÃO =================
@Header("Simulation configs")
@Order(idx = 0)
public float gravity = -9.8f;

@Order(idx = 2)
public boolean useFixedTimeStep = false;

@Order(idx = 1) @ShowIf("useFixedTimeStep")
public float timeStep = 0.016f;


@Order(idx = 3)
public int Mesh_iterations = 4; // 4-6 low | 7-15 mid | 20-... high

// ================= CONFIG — FIXAÇÃO =================
@Header("Pre-Fixation Configs")
@Order(idx = 10)
public boolean fixLeft=true;

@Order(idx = 11)
public boolean fixRight;

@Order(idx = 12)
public boolean fixTop;

@Order(idx = 13)
public boolean fixBottom;

// ================= CONFIG — FEATURES =================
@Header ("Util Features")
@Order(idx = 20)
public boolean enableWind;

@Order(idx = 22)
public boolean seamConstraints;


// ================= CONFIG — VENTO =================
@Header("Wind Config")
@Order(idx = 30)
public Vector3 windDir = new Vector3(1, 0, 0).normalize();

@Order(idx = 31)
public float windStrength = 2f;

@Order(idx = 32)
public float turbulence = 1.2f;

// ================= CONFIG — COLISORES =================

@Header("Rb_Collision config")
@Order(idx = 42)
public boolean collideMeshes;

private boolean noCollideMeshes=collideMeshes;

@Order(idx = 23) @ShowIf("collideMeshes")
public boolean useAdvancedBendings;

@Order(idx = 43) @ShowIf("collideMeshes")
float RB_pointRadius = 0.2f;

@Header("Collisions List")
@Order(idx = 40) @ShowIf("noCollideMeshes")
public List<SpatialObject> boxColliders = new ArrayList<SpatialObject>();

@Order(idx = 41) @ShowIf("noCollideMeshes")
public List<SpatialObject> sphereColliders = new ArrayList<SpatialObject>();


// ================= CONFIG — CONSTRAINTS =================
@Header("Constraints Configs")

@Order(idx = 50) @ShowIf("noCollideMeshes")
public float structuralStiffness = 0.85f;

@Order(idx = 51) @ShowIf("noCollideMeshes")
public float bendingStiffness = 0.6f;

@Order(idx = 52) @ShowIf("noCollideMeshes")
public float seamStiffness = 1.0f;

/////////////// mass/////////////////////
@Header("Cloth general Configs")

@Order(idx = 60)
public float mass = 1.0f; // 0.5 leve | 1 normal | 2 pesado

@Order(idx = 61)
public float airDrag = 0.02f; // 0 = sem drag | 0.01~0.05 bom

@Order(idx = 62)
float damping = 0.999f;

/////// RigidBODYS mode/////////
@Header("Cloth_RB general Configs")

@Order(idx = 64) @ShowIf("collideMeshes")
float RB_compliance = 5f;

@Order(idx = 65) @ShowIf("collideMeshes")
float RB_friction = 0.1f;

@Order(idx = 65) @ShowIf("collideMeshes")
float Internalfriction = 1f;

@Order(idx = 66) @ShowIf("collideMeshes")
float RB_beta = 0.85f;

@Order(idx = 67) @ShowIf("collideMeshes")
float RB_slop = 0.000f;

@Order(idx = 68) @ShowIf("collideMeshes")
float RB_dampingCoefficient = 0.1f;
@Header("Metal/plastic settings")

@Order(idx = 69) @ShowIf("collideMeshes")
public boolean showMetalSettings;


// 0 = plasticidade desligada (cloth puro)
@Order(idx = 69) @ShowIf("showMetalSettings")
public float yieldStrain = 0f;

// 0 = sem deformação permanente
@Order(idx = 70) @ShowIf("showMetalSettings")
public float plasticRate = 0f;

// Limite de deformação permanente (metal)
@Order(idx = 71) @ShowIf("showMetalSettings")
public float maxPlasticStrain = 0.3f;

// Endurecimento progressivo
@Order(idx = 72) @ShowIf("showMetalSettings")
public float hardening = 0f;

// ================= RUNTIME — CLOTH =================

@Order(idx = 100)
private clothPoint[] points;

@Order(idx = 101)
private List<ClothConstraint> constraints = new ArrayList<ClothConstraint>();

// ================= RUNTIME — MESH =================

@Order(idx = 110)
private Vertex mesh;

@Order(idx = 111)
private NativeFloatBuffer vertexBuffer;

@Order(idx = 112)
private float[] tempVerts;

@Order(idx = 113)
private Vector3 lastObjectPos = new Vector3();
// ================= RUNTIME — ESTADO =================

@Order(idx = 120)
private boolean editorMode;

@Order(idx = 121)
private boolean meshDirty;

@Order(idx = 122)
private float windTime;

// ================= Editor =================
@Header("Editor")

@Order(idx = -100)
boolean Editor_Mode;

@Order(idx = -90) 
private boolean UpdateMesh;

@Order(idx = -80) @ShowIf("Editor_Mode")
boolean Clearall;

@Order(idx = -70) @ShowIf("Editor_Mode")
boolean resetPointsPos = false;

@Order(idx = -60) @ShowIf("Editor_Mode") 
boolean DestroyPointsInPlay = true;

private int atualizado;
@AutoWired private ModelRenderer mr;
private Vector3 pospassado = new Vector3();
private boolean pastcollideMeshes;

public void stoppedRepeat() {
    
  if (myObject.getChildCount() > 0) {
    SpatialObject obj = myObject.findChildObject("ponto-" + 0);
    if (obj != null) Editor_Mode = true;
    atualizado = 1;
    clothPoint cp = obj.findComponent(clothPoint.class);

    /////// gambiarra pra caso os pontos bugarem e forem parar fora do lugar /////////

    float eps = 0.0005f; // tolerância do editor
    if (!collideMeshes) {
      if (cp.localPosition.sub(obj.getPosition()).length() > eps) {
        if (!resetPointsPos) {
          resetPointsPos = true;
        }
      } else {
        resetPointsPos = false;
      }
    } else {

      Vector3 posatual = myObject.getGlobalPosition();

      if (pospassado != posatual) {
        ModelRenderer mr = myObject.findComponent(ModelRenderer.class);

        mesh = mr.getVertex();
        NativeFloatBuffer originalVertexBuf;
        if (mesh != null) originalVertexBuf = mesh.getVerticesBuffer();
        else return;

        int totalFloats = originalVertexBuf.capacity();
        int vertexCount = totalFloats / 3;

        if (tempVerts == null || tempVerts.length != totalFloats) {
          tempVerts = new float[totalFloats];
        }

        for (int i = 0; i < vertexCount; i++) {

          SpatialObject pobj = myObject.findChildObject("ponto-" + i);
          if (pobj == null) continue;

          clothPoint p = pobj.findComponent(clothPoint.class);
          if (p == null) continue;

          int b = i * 3;

          // posição original da malha
          Vector3 local = new Vector3(originalVertexBuf.get(b), originalVertexBuf.get(b + 1), originalVertexBuf.get(b + 2));

          Vector3 world = myObject.getTransform().transformPoint(local);

          // resetar dados do ponto
          p.localPosition = local;
          p.position = world;
          p.previousPosition = world.copy();

          pobj.setGlobalPosition(p.position);
          pobj.getTransform().recalculateMatrices();
          pospassado = posatual.copy();
        }
      }
    }
  }

  if (resetPointsPos) {
    if (resetPointsPos && Editor_Mode) {

      ModelRenderer mr = myObject.findComponent(ModelRenderer.class);
      mesh = mr.getVertex();
      NativeFloatBuffer originalVertexBuf = mesh.getVerticesBuffer();

      int totalFloats = originalVertexBuf.capacity();
      int vertexCount = totalFloats / 3;

      if (tempVerts == null || tempVerts.length != totalFloats) {
        tempVerts = new float[totalFloats];
      }

      for (int i = 0; i < vertexCount; i++) {

        SpatialObject pobj = myObject.findChildObject("ponto-" + i);
        if (pobj == null) continue;

        clothPoint p = pobj.findComponent(clothPoint.class);
        if (p == null) continue;

        int b = i * 3;

        // posição original da malha
        Vector3 local = new Vector3(originalVertexBuf.get(b), originalVertexBuf.get(b + 1), originalVertexBuf.get(b + 2));

        Vector3 world = myObject.getTransform().transformPoint(local);

        // resetar dados do ponto
        p.localPosition = local;
        p.position = world;
        p.previousPosition = world.copy();

        if (!collideMeshes) pobj.setPosition(myObject.getTransform().inverseTransformPoint(world));
        else pobj.setGlobalPosition(p.position);

        // atualizar malha
        tempVerts[b] = local.getX();
        tempVerts[b + 1] = local.getY();
        tempVerts[b + 2] = local.getZ();
      }

      resetPointsPos = false;
      UpdateMesh = true;
    }
  }

  if (collideMeshes && !Editor_Mode) {
    Editor_Mode = true;
   noCollideMeshes=!collideMeshes;

    atualizado = 0;
    DestroyPointsInPlay = false;
  }

  if ((!collideMeshes && pastcollideMeshes == true) && Editor_Mode) {
    Clearall = true;
       noCollideMeshes=!collideMeshes;

    //   Editor_Mode=false;
  }
  if (useAdvancedBendings && !collideMeshes) useAdvancedBendings = false;

  pastcollideMeshes = collideMeshes;

  if (Editor_Mode) {
    // === INICIALIZAÇÃO ===
    if (atualizado == 0) {
      ModelRenderer mr = myObject.findComponent(ModelRenderer.class);
      mesh = mr.getVertex();
      NativeFloatBuffer originalVertexBuf = mesh.getVerticesBuffer();
      int totalFloats = originalVertexBuf.capacity();
      int vertexCount = totalFloats / 3;

      vertexBuffer = new NativeFloatBuffer(totalFloats, NativeFloatBuffer.Precision.FLOAT16);
      for (int i = 0; i < totalFloats; i++) {
        vertexBuffer.put(originalVertexBuf.get(i));
      }
      mesh.setVertices(vertexBuffer); // opcional

      points = new clothPoint[vertexCount];

      for (int i = 0; i < vertexCount; i++) {
        int b = i * 3;
        Vector3 local = new Vector3(vertexBuffer.get(b), vertexBuffer.get(b + 1), vertexBuffer.get(b + 2));
        Vector3 world = myObject.getTransform().transformPoint(local);

        SpatialObject pobj = new SpatialObject("ponto-" + i);

        clothPoint p = new clothPoint();
        pobj.addComponent(p);
        p.position = world;
        p.previousPosition = world.copy();
        p.localPosition = local;
        p.vertexIndex = i;
        if (!collideMeshes) pobj.setPosition(myObject.getTransform().inverseTransformPoint(p.position));
        else pobj.setGlobalPosition(p.position);

        pobj.setParent(myObject);
        pobj.getTransform().recalculateMatrices();
        points[i] = p;
      }

      atualizado = 1;
    }

    if (collideMeshes) {
      enablePhysicsProxies();

      if (DestroyPointsInPlay) DestroyPointsInPlay = false;
    }

    // === ATUALIZAÇÃO VISUAL ===
    if (!UpdateMesh && points != null) {
      ModelRenderer mr = myObject.findComponent(ModelRenderer.class);
      mesh = mr.getVertex();
      NativeFloatBuffer originalVertexBuf = mesh.getVerticesBuffer();
      int totalFloats = originalVertexBuf.capacity();
      int vertexCount = totalFloats / 3;

      if (vertexBuffer == null) {
        vertexBuffer = new NativeFloatBuffer(totalFloats, NativeFloatBuffer.Precision.FLOAT16);
        for (int i = 0; i < totalFloats; i++) {
          vertexBuffer.put(originalVertexBuf.get(i));
        }
        mesh.setVertices(vertexBuffer);
      }

      if (tempVerts == null || tempVerts.length != totalFloats) {
        tempVerts = new float[totalFloats];
      }

      for (int i = 0; i < vertexCount; i++) {
        SpatialObject pobj = myObject.findChildObject("ponto-" + i);
        if (pobj != null) {
          clothPoint p = pobj.findComponent(clothPoint.class);
          p.position = pobj.getGlobalPosition();
          p.previousPosition = pobj.getGlobalPosition();
        }
      }

      for (int i = 0; i < vertexCount; i++) {
        Vector3 local = myObject.getTransform().inverseTransformPoint(points[i].position);
        int idx = i * 3;
        tempVerts[idx] = local.getX();
        tempVerts[idx + 1] = local.getY();
        tempVerts[idx + 2] = local.getZ();
      }

      vertexBuffer.set(tempVerts);
      mesh.recalculateBoundingBox();
      mesh.apply();

      UpdateMesh = true;
    }
  }

  if (Clearall) {
    // === SAÍDA DO MODO EDITOR ===
    for (int i = 0; i < myObject.getChildCount(); i++) {
      SpatialObject pobj = myObject.findChildObject("ponto-" + i);
      if (pobj != null) pobj.destroy();
    }
    Editor_Mode = false;
    Clearall = false;
    atualizado = 0;
  }
}

@Override
public void start() {

  lastObjectPos.set(myObject.getGlobalPosition());

  mesh = mr.getVertex();
  NativeFloatBuffer src = mesh.getVerticesBuffer();

  // ===============================
  // DETECTAR SE VEIO DO EDITOR
  // ===============================
  boolean fromEditor = Editor_Mode;

  int vCount = src.capacity() / 3;

  // ===============================
  // USAR BUFFER ATUAL (EDITOR OU NÃO)
  // ===============================
  vertexBuffer = src;
  tempVerts = new float[src.capacity()];

  // ===============================
  // CAMINHO 1 — VEIO DO EDITOR
  // ===============================
  if (fromEditor) {

    points = new clothPoint[vCount];

    for (int i = 0; i < vCount; i++) {

      SpatialObject pobj = myObject.findChildObject("ponto-" + i);
      if (pobj == null) continue;

      clothPoint p = pobj.findComponent(clothPoint.class);
      if (p == null) continue;

      // garantir consistência
      p.vertexIndex = i;
      p.position = pobj.getGlobalPosition();
      p.previousPosition = p.position.copy();

      // recalcular local a partir do estado editado
      p.localPosition = myObject.getTransform().inverseTransformPoint(p.position);

      points[i] = p;
      syncPointsFromEditor();
    }

    // limpar gizmos do editor
    for (int i = 0; i < myObject.getChildCount(); i++) {
      SpatialObject c = myObject.findChildObject("ponto-" + i);
      if (c != null) {
        if (DestroyPointsInPlay) c.destroy();
      }
    }

  }
  // ===============================
  // CAMINHO 2 — SEM EDITOR
  // ===============================
  else {

    points = new clothPoint[vCount];

    for (int i = 0; i < vCount; i++) {

      int b = i * 3;

      Vector3 local = new Vector3(vertexBuffer.get(b), vertexBuffer.get(b + 1), vertexBuffer.get(b + 2));

      Vector3 world = myObject.getTransform().transformPoint(local);

      clothPoint p = new clothPoint();
      p.vertexIndex = i;
      p.localPosition = local;
      p.position = world;
      p.previousPosition = world.copy();

      points[i] = p;
      createPoints();
    }
  }

  // ===============================
  // CONSTRAINTS SEMPRE RECRIADAS
  // ===============================
  constraints.clear();
  createConstraints();

  mesh.apply();
}

//////// estranho/////
private void createConstraints() {

  NativeIntBuffer tris = mesh.getTrianglesBuffer();
  Set<Long> edges = new HashSet<Long>();

  // ========= STRUCTURAL =========
  for (int i = 0; i < tris.capacity(); i += 3) {

    int a = tris.get(i);
    int b = tris.get(i + 1);
    int c = tris.get(i + 2);

    addEdge(a, b, edges);
    addEdge(b, c, edges);
    addEdge(c, a, edges);
  }
  //   createShearConstraints(tris);
  // ========= BENDING =========
  if (Mesh_iterations > 7 && !useAdvancedBendings) {
    createBendingConstraints(tris);
  }
  if (Mesh_iterations > 7 && useAdvancedBendings) {
    createAdvancedBendingConstraints(tris);
  }

  // ========= SEAMS =========
  if (seamConstraints) {
    createSeamConstraints();
  }
}

private void addEdge(int a, int b, Set<Long> edges) {

  int min = Math.min(a, b);
  int max = Math.max(a, b);

  long key = (((long) min) << 32) | (max & 0xffffffffL);
  if (edges.contains(key)) return;

  edges.add(key);
  addConstraint(points[min], points[max], structuralStiffness, ClothConstraint.Type.STRUCTURAL);
}

private void createSeamConstraints() {

  Map<Long, Integer> firstAt = new HashMap<Long, Integer>();

  for (int i = 0; i < points.length; i++) {

    Vector3 p = points[i].localPosition;

    long kx = (long) (p.getX() * 100000f);
    long ky = (long) (p.getY() * 100000f);
    long kz = (long) (p.getZ() * 100000f);

    long key = (kx & 0x1fffffL) << 42 | (ky & 0x1fffffL) << 21 | (kz & 0x1fffffL);

    if (!firstAt.containsKey(key)) {

      firstAt.put(key, i);

    } else {

      int currentMaster = firstAt.get(key);

      // Se o ponto atual for fixo e o master não for, troca
      if (points[i].isFixed && !points[currentMaster].isFixed) {
        firstAt.put(key, i);
        currentMaster = i;
      }

      addConstraint(points[currentMaster], points[i], seamStiffness, ClothConstraint.Type.STRUCTURAL);

      if (collideMeshes) {
        SpatialObject slaveObj = myObject.findChildObject("ponto-" + i);
        if (slaveObj != null) {
          Collider col = slaveObj.findComponent(Collider.class);
          if (col != null) col.setEnabled(false);
        }
      }
    }
  }
}

private void createBendingConstraints(NativeIntBuffer tris) {

  Map<Long, int[]> edgeToTri = new HashMap<Long, int[]>();

  // mapear arestas → triângulos
  for (int i = 0; i < tris.capacity(); i += 3) {

    int a = tris.get(i);
    int b = tris.get(i + 1);
    int c = tris.get(i + 2);

    mapEdge(edgeToTri, a, b, i);
    mapEdge(edgeToTri, b, c, i);
    mapEdge(edgeToTri, c, a, i);
  }

  // criar bending
  for (Map.Entry<Long, int[]> e : edgeToTri.entrySet()) {

    int[] ts = e.getValue();
    if (ts[1] == -1) continue;

    int t1 = ts[0];
    int t2 = ts[1];

    int a1 = tris.get(t1);
    int b1 = tris.get(t1 + 1);
    int c1 = tris.get(t1 + 2);

    int a2 = tris.get(t2);
    int b2 = tris.get(t2 + 1);
    int c2 = tris.get(t2 + 2);

    int[] shared = sharedEdge(a1, b1, c1, a2, b2, c2);
    if (shared == null) continue;

    int o1 = opposite(a1, b1, c1, shared[0], shared[1]);
    int o2 = opposite(a2, b2, c2, shared[0], shared[1]);

    if (o1 != -1 && o2 != -1) {
      addConstraint(points[o1], points[o2], bendingStiffness, ClothConstraint.Type.BENDING);
    }
  }
}

private void createShearConstraints(NativeIntBuffer tris) {

  Map<Long, int[]> edgeToTri = new HashMap<Long, int[]>();

  // mapear arestas → triângulos
  for (int i = 0; i < tris.capacity(); i += 3) {

    int a = tris.get(i);
    int b = tris.get(i + 1);
    int c = tris.get(i + 2);

    mapEdge(edgeToTri, a, b, i);
    mapEdge(edgeToTri, b, c, i);
    mapEdge(edgeToTri, c, a, i);
  }

  // criar bending
  for (Map.Entry<Long, int[]> e : edgeToTri.entrySet()) {

    int[] ts = e.getValue();
    if (ts[1] == -1) continue;

    int t1 = ts[0];
    int t2 = ts[1];

    int a1 = tris.get(t1);
    int b1 = tris.get(t1 + 1);
    int c1 = tris.get(t1 + 2);

    int a2 = tris.get(t2);
    int b2 = tris.get(t2 + 1);
    int c2 = tris.get(t2 + 2);

    int[] shared = sharedEdge(a1, b1, c1, a2, b2, c2);
    if (shared == null) continue;

    int o1 = opposite(a1, b1, c1, shared[0], shared[1]);
    int o2 = opposite(a2, b2, c2, shared[0], shared[1]);

    // ==========================================================
    // 2. NOVO: Constraint de SHEAR (Reforço Cruzado)
    // ==========================================================
    // Isso conecta as pontas opostas com uma mola MAIS DURA que o bending,
    // mas um pouco mais mole que o structural.
    // Isso impede que os dois triângulos se separem excessivamente.

    float shearStiffness = structuralStiffness * 0.7f; // Ex: 50% da rigidez estrutural
    addConstraint(points[o1], points[o2], shearStiffness, ClothConstraint.Type.SHEAR);
  }
}

private void createAdvancedBendingConstraints(NativeIntBuffer tris) {

  Map<Long, int[]> edgeToTri = new HashMap<Long, int[]>();

  // mapear arestas → triângulos
  for (int i = 0; i < tris.capacity(); i += 3) {

    int a = tris.get(i);
    int b = tris.get(i + 1);
    int c = tris.get(i + 2);

    mapEdge(edgeToTri, a, b, i);
    mapEdge(edgeToTri, b, c, i);
    mapEdge(edgeToTri, c, a, i);
  }

  // criar bending
  for (Map.Entry<Long, int[]> e : edgeToTri.entrySet()) {

    int[] ts = e.getValue();
    if (ts[1] == -1) continue;

    int t1 = ts[0];
    int t2 = ts[1];

    int a1 = tris.get(t1);
    int b1 = tris.get(t1 + 1);
    int c1 = tris.get(t1 + 2);

    int a2 = tris.get(t2);
    int b2 = tris.get(t2 + 1);
    int c2 = tris.get(t2 + 2);

    int[] shared = sharedEdge(a1, b1, c1, a2, b2, c2);
    if (shared == null) continue;

    int o1 = opposite(a1, b1, c1, shared[0], shared[1]);
    int o2 = opposite(a2, b2, c2, shared[0], shared[1]);

    // No seu loop de criação de bending:
    if (o1 != -1 && o2 != -1) {
      // shared[0], shared[1] -> p1, p2 (Aresta)
      // o1 -> p3 (Ponta 1)
      // o2 -> p4 (Ponta 2)
      addBendingConstraintAdvanced(points[shared[0]], points[shared[1]], points[o1], points[o2], bendingStiffness);
    }
  }
}

private void mapEdge(Map<Long, int[]> map, int a, int b, int tri) {

  int min = Math.min(a, b);
  int max = Math.max(a, b);
  long key = (((long) min) << 32) | (max & 0xffffffffL);

  int[] t = map.get(key);
  if (t == null) {
    map.put(key, new int[] {tri, -1});
  } else {
    t[1] = tri;
  }
}

private int[] sharedEdge(int a1, int b1, int c1, int a2, int b2, int c2) {
  int[] s = new int[2];
  int n = 0;

  int[] t1 = {a1, b1, c1};
  int[] t2 = {a2, b2, c2};

  for (int i : t1) for (int j : t2) if (i == j && n < 2) s[n++] = i;

  return n == 2 ? s : null;
}

private int opposite(int a, int b, int c, int e1, int e2) {
  if (a != e1 && a != e2) return a;
  if (b != e1 && b != e2) return b;
  if (c != e1 && c != e2) return c;
  return -1;
}

private void createPoints() {

  Vector3 min = new Vector3(Float.MAX_VALUE, 0, Float.MAX_VALUE);
  Vector3 max = new Vector3(Float.MIN_VALUE, 0, Float.MIN_VALUE);

  for (int i = 0; i < points.length; i++) {
    int b = i * 3;
    Vector3 local = new Vector3(vertexBuffer.get(b), vertexBuffer.get(b + 1), vertexBuffer.get(b + 2));

    min.set(Math.min(min.getX(), local.getX()), 0, Math.min(min.getZ(), local.getZ()));
    max.set(Math.max(max.getX(), local.getX()), 0, Math.max(max.getZ(), local.getZ()));
  }

  float eps = 0.001f;

  for (int i = 0; i < points.length; i++) {

    int b = i * 3;
    Vector3 local = new Vector3(vertexBuffer.get(b), vertexBuffer.get(b + 1), vertexBuffer.get(b + 2));

    Vector3 world = myObject.getTransform().transformPoint(local);

    clothPoint p = new clothPoint();
    p.vertexIndex = i;
    p.position = world;
    p.previousPosition = world.copy();
    p.localPosition = local;

    boolean fixed = (fixLeft && Math.abs(local.getX() - min.getX()) < eps) || (fixRight && Math.abs(local.getX() - max.getX()) < eps) || (fixTop && Math.abs(local.getZ() - max.getZ()) < eps) || (fixBottom && Math.abs(local.getZ() - min.getZ()) < eps);

    p.isFixed = fixed;
    points[i] = p;
  }
}

private void syncPointsFromEditor() {

  int vCount = vertexBuffer.capacity() / 3;
  points = new clothPoint[vCount];

  Vector3 min = new Vector3(Float.MAX_VALUE, 0, Float.MAX_VALUE);
  Vector3 max = new Vector3(Float.MIN_VALUE, 0, Float.MIN_VALUE);

  // === calcular bounds a partir do estado EDITADO ===
  for (int i = 0; i < vCount; i++) {
    SpatialObject pobj = myObject.findChildObject("ponto-" + i);
    if (pobj == null) continue;

    Vector3 local = myObject.getTransform().inverseTransformPoint(pobj.getGlobalPosition());

    min.set(Math.min(min.getX(), local.getX()), 0, Math.min(min.getZ(), local.getZ()));

    max.set(Math.max(max.getX(), local.getX()), 0, Math.max(max.getZ(), local.getZ()));
  }

  float eps = 0.001f;

  // === sincronizar pontos ===
  for (int i = 0; i < vCount; i++) {

    SpatialObject pobj = myObject.findChildObject("ponto-" + i);
    if (pobj == null) continue;

    clothPoint p = pobj.findComponent(clothPoint.class);
    if (p == null) continue;

    Vector3 world = pobj.getGlobalPosition();
    Vector3 local = myObject.getTransform().inverseTransformPoint(world);

    p.vertexIndex = i;
    p.position = world;
    p.previousPosition = world.copy();
    p.localPosition = local;

    // 🔥 só aplica fix automático se NÃO estiver fixado manualmente
    if (!p.isFixed) {

      boolean fixed = (fixLeft && Math.abs(local.getX() - min.getX()) < eps) || (fixRight && Math.abs(local.getX() - max.getX()) < eps) || (fixTop && Math.abs(local.getZ() - max.getZ()) < eps) || (fixBottom && Math.abs(local.getZ() - min.getZ()) < eps);

      p.isFixed = fixed;
    }

    points[i] = p;
  }
}

////////////////// q agonia de codigo slk
@Override
public void repeat() {

  // ===== FOLLOW OBJECT =====
  Vector3 cur = myObject.getTransform().getGlobalPosition();
  Vector3 delta = cur.sub(lastObjectPos);

  if (delta.getX() != 0 || delta.getY() != 0 || delta.getZ() != 0) {
    for (clothPoint p : points) {
      if (p == null) continue;
      if (p.rb == null) {
        p.position = p.position.sum(delta);
        p.previousPosition = p.previousPosition.sum(delta);
      } else {
        p.myObject.position = p.myObject.position.sum(delta);
        p.previousPosition = p.previousPosition.sum(delta);
      }
    }
  }
  myObject.getTransform().recalculateMatrices();
  lastObjectPos.set(cur);

  if (points == null || points.length == 0) return;
  windTime += Time.deltaTime;

  // === INTEGRA CLOTH ===
  Vector3 g = new Vector3(0, gravity, 0);
 
 
    for (ClothConstraint c : constraints) {
      c.lambdaAccum = 0f;
    }
    
  for (clothPoint p : points) {
 
      if (p == null || (p.isFixed && !collideMeshes)) continue;

      p.applyForce(g.mul(mass));
      if (enableWind) applyWind(p);
      if (!useFixedTimeStep) p.simulate(Time.deltaTime, damping,airDrag);
      else p.simulate(timeStep, damping,airDrag);
     
    }

    // === CONSTRAINTS ===
    // 2️⃣ Resolve as iterações dentro deste micro-passo
    for (int i = 0; i < Mesh_iterations; i++) {

      for (ClothConstraint c : constraints) {
        c.satisfy(RB_compliance, RB_beta, RB_slop, RB_dampingCoefficient, Internalfriction, yieldStrain, plasticRate, maxPlasticStrain, hardening, timeStep,Time.deltaTime,useFixedTimeStep);
      }
    }

    // ===== COLLISIONS =====
  
     if (!boxColliders.isEmpty()) collideBoxes();
    if (!sphereColliders.isEmpty()) collideSpheres();

  // ===== UPDATE MESH =====
  updateVertexPositions();
}

private void updateVertexPositions() {

  for (int i = 0; i < points.length; i++) {
    if (points[i].rb == null) {

      Vector3 local = myObject.getTransform().inverseTransformPoint(points[i].position);

      int b = i * 3;
      tempVerts[b] = local.getX();
      tempVerts[b + 1] = local.getY();
      tempVerts[b + 2] = local.getZ();

    } else {

      // ERRO ANTERIOR: usar points[i].position
      // CORREÇÃO: usar a posição global real do objeto que o RB moveu
      Vector3 globalPos = points[i].myObject.getGlobalPosition();
      Vector3 local = myObject.getTransform().inverseTransformPoint(globalPos);

      int b = i * 3;
      tempVerts[b] = local.getX();
      tempVerts[b + 1] = local.getY();
      tempVerts[b + 2] = local.getZ();
    }
  }

  vertexBuffer.set(tempVerts);
  mesh.apply(); // pesado, mas 1x só
}

/////////// vento ////////////

private void addConstraint(clothPoint p1, clothPoint p2, float stiffness, ClothConstraint.Type type) {
  ClothConstraint c = new ClothConstraint();

  c.p1 = p1;
  c.p2 = p2;
  c.stiffness = stiffness;
  c.type = type;

  c.start(); // calcula restLength, etc

  constraints.add(c);
}

private void addBendingConstraintAdvanced(clothPoint edge1, clothPoint edge2, clothPoint tip1, clothPoint tip2, float stiffness) {
  ClothConstraint c = new ClothConstraint();

  // Aresta compartilhada (a dobradiça)
  c.p1 = edge1;
  c.p2 = edge2;

  // Pontos opostos (as abas que dobram)
  c.p3 = tip1;
  c.p4 = tip2;

  c.stiffness = stiffness;
  c.type = ClothConstraint.Type.BENDING_ADVANCED;

  // No start(), ele vai calcular o restAngleValue em vez do restLength
  c.start();

  constraints.add(c);
}

/////////// vento ////////////
private float simpleNoise(float x, float y, float z) {

  float v = (float) Math.sin(x * 12.9898f + y * 78.233f + z * 37.719f) * 43758.5453f;

  return v - (int) v; // fract
}

private void applyWind(clothPoint p) {

  float n = simpleNoise(p.position.getX() * 0.1f, 0, windTime * 0.5f);

  Vector3 dir = windDir.sum(new Vector3(n, n * 0.3f, n)).normalize();

  Vector3 f = dir.mul(windStrength * (1f + turbulence * n));
  p.applyForce(f);
}

///////// colisoes /////////////////
private void collideBoxes() {

  for (SpatialObject box : boxColliders) {

    Vector3 center = box.getTransform().getGlobalPosition();
    Vector3 half = box.getTransform().getGlobalScale().mul(0.53f);

    Vector3 min = center.sub(half);
    Vector3 max = center.sum(half);

    for (clothPoint p : points) {

      if (p.isFixed) continue;

      Vector3 pos = p.position;

      if (pos.getX() < min.getX() || pos.getX() > max.getX()) continue;
      if (pos.getY() < min.getY() || pos.getY() > max.getY()) continue;
      if (pos.getZ() < min.getZ() || pos.getZ() > max.getZ()) continue;

      float dx = Math.min(pos.getX() - min.getX(), max.getX() - pos.getX());
      float dy = Math.min(pos.getY() - min.getY(), max.getY() - pos.getY());
      float dz = Math.min(pos.getZ() - min.getZ(), max.getZ() - pos.getZ());

      Vector3 corr;

      if (dx <= dy && dx <= dz) {
        corr = new Vector3(pos.getX() - min.getX() < max.getX() - pos.getX() ? -dx : dx, 0, 0);
      } else if (dy <= dz) {
        corr = new Vector3(0, pos.getY() - min.getY() < max.getY() - pos.getY() ? -dy : dy, 0);
      } else {
        corr = new Vector3(0, 0, pos.getZ() - min.getZ() < max.getZ() - pos.getZ() ? -dz : dz);
      }

      p.position = p.position.sum(corr);

      // damping de impacto
      Vector3 vel = p.position.sub(p.previousPosition).mul(0.5f);
      p.previousPosition = p.position.sub(vel);
    }
  }
}

private void collideSpheres() {

  for (SpatialObject sph : sphereColliders) {

    Vector3 center = sph.getTransform().getGlobalPosition();
    float radius = sph.getTransform().getGlobalScale().getX() * 0.55f;

    for (clothPoint p : points) {

      if (p.isFixed) continue;

      Vector3 d = p.position.sub(center);
      float dist = d.length();

      if (dist >= radius || dist == 0f) continue;

      Vector3 corr = d.normalize().mul(radius - dist);
      p.position = p.position.sum(corr);

      Vector3 vel = p.position.sub(p.previousPosition).mul(0.6f);
      p.previousPosition = p.position.sub(vel);
    }
  }
}

private void enablePhysicsProxies() {
  if (!collideMeshes) return; // só ativa no modo rigidbody

  float radius = RB_pointRadius;
  if (points != null) {
    for (int i = 0; i < points.length; i++) {
      clothPoint p = points[i];
      if (p == null) continue;

      SpatialObject pobj = myObject.findChildObject("ponto-" + i);
      if (pobj == null) continue;

      // ajusta escala do objeto para o raio do ponto
      pobj.setScale(radius, radius, radius);
      pobj.getTransform().setIgnoreParent(true);
      // cria collider se não existir
      Collider sc = pobj.findComponent(Collider.class);
      if (sc == null) {
        sc = new Collider();
        sc.setShape(sc.getShape().valueOf("Sphere"));
        pobj.addComponent(sc);
      }

      // cria rigidbody se não existir
      Rigidbody rb = pobj.findComponent(Rigidbody.class);
      if (rb == null) {
        rb = new Rigidbody();
        rb.setMass(mass);
        rb.setGravityEnabled(false);

        // trava rotação
        rb.setFreezeRX(true);
        rb.setFreezeRY(true);
        rb.setFreezeRZ(true);

        // damping e fricção
        rb.setLinearDamping(0.1f);
        rb.setAngularDamping(0.2f);
        rb.setFriction(RB_friction);

        pobj.addComponent(rb);
      }
    }
  }
}