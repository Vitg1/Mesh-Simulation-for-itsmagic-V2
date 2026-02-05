/* @Author Sr Mil Games */
/* @Author Srmilgames */
public int width = 10;
public int height = 10;
public float spacing = 0.2f;
boolean meshBuilt = true;

private ModelRenderer mrsafe;

public void stoppedRepeat() {
    if(mrsafe==null)
    mrsafe=myObject.findComponent(ModelRenderer.class);
    
  if (mrsafe != null) {
      if(mrsafe.getVertex()==null && mrsafe.getMaterial() !=null)
      fazer();
  } 

  // === RODA SOMENTE SE USUÁRIO CLICAR ===
  if (meshBuilt) {
    fazer();
    meshBuilt = false;
  }
}

public void fazer() {
  int vertexCount = width * height;
  int quadCount = (width - 1) * (height - 1);
  int indexCount = quadCount * 6;

  NativeFloatBuffer vertexBuffer = new NativeFloatBuffer(vertexCount * 3);
  NativeIntBuffer triangleBuffer = new NativeIntBuffer(indexCount);
  NativeFloatBuffer uvBuffer = new NativeFloatBuffer(vertexCount * 2);
  for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++) {
      float u = (float) x / (width - 1);
      float v = (float) y / (height - 1);
      uvBuffer.put(u);
      uvBuffer.put(v);
    }
  }
  // Gerar vértices
  for (int y = 0; y < height; y++) {
    for (int x = 0; x < width; x++) {
      float fx = x * spacing;
      float fy = 0.0f;
      float fz = y * spacing;

      vertexBuffer.put(fx); // X
      vertexBuffer.put(fy); // Y
      vertexBuffer.put(fz); // Z
    }
  }

  // Gerar triângulos (6 índices por quad)
  for (int y = 0; y < height - 1; y++) {
    for (int x = 0; x < width - 1; x++) {
      int topLeft = y * width + x;
      int topRight = topLeft + 1;
      int bottomLeft = topLeft + width;
      int bottomRight = bottomLeft + 1;

      // Primeiro triângulo
      triangleBuffer.put(topLeft);
      triangleBuffer.put(bottomLeft);
      triangleBuffer.put(topRight);

      // Segundo triângulo
      triangleBuffer.put(topRight);
      triangleBuffer.put(bottomLeft);
      triangleBuffer.put(bottomRight);
    }
  }

  // Resetar posição dos buffers antes de aplicar (por segurança)
  vertexBuffer.position(0);
  triangleBuffer.position(0);

  // Aplicar na malha
  Vertex vertex = new Vertex();
  vertex.setVertices(vertexBuffer);
  vertex.setTriangles(triangleBuffer);
  vertex.setUVs(uvBuffer);
  vertex.recalculateBoundingBox();
  vertex.apply();

  ModelRenderer mr = myObject.findComponent(ModelRenderer.class);
  Material mt = mr.getMaterial();
  mr.setVertex(vertex);
  mr.setMaterial(mt);
}