public enum Type {
  STRUCTURAL,
  SHEAR,
  BENDING,
  BENDING_ADVANCED
}

public clothPoint p1;
public clothPoint p2;
public clothPoint p3; // Novo: Ponta do triângulo 1
public clothPoint p4; // Novo: Ponta do triângulo 2

public float restAngle; // Novo: Cosseno de repouso para metal/cloth advanced
public float restLength;
public float stiffness;
public Type type;
public float lambdaAccum = 0f;
float compliance;
float currentLength;
Vector3 delta = new Vector3();
boolean hasRb;
// =======================
// MATERIAL / PLASTICIDADE
// =======================

private float hardeningAccum = 0f;
float plasticStrain = 0.0f;
// Referência
private float originalRestLength;

float sign(float v) {
  return v > 0 ? 1f : (v < 0 ? -1f : 0f);
}

public void start() {
  if (type == Type.BENDING_ADVANCED) {
    // Agora salva o ângulo real em radianos
    restAngle = calculateCurrentAngleRad();
  } else {
    restLength = p1.position.distance(p2.position);
    originalRestLength = restLength;
  }
}

private float calculateCurrentAngleRad() {
  Vector3 e = p2.position.sub(p1.position);
  float elen = e.length();
  if (elen < 1e-7f) return 0f;

  // Normais
  Vector3 n1 = e.cross(p3.position.sub(p1.position)).normalize();
  Vector3 n2 = e.cross(p4.position.sub(p1.position)).normalize();

  float dot = n1.dot(n2);
  // Clamp para evitar NaN no acos
  dot = Math.max(-1f, Math.min(1f, dot));
  
  return (float) Math.acos(dot); // Retorna radianos (0 a 3.1415...)
}

void PBD() { // Lógica PBD (sem RB) - Mantida
  float diff = (currentLength - restLength) / currentLength;
  float adjustment = stiffness;
  if (type == Type.SHEAR) adjustment *= 0.6f;
  else if (type == Type.BENDING) adjustment *= 0.2f;
  Vector3 correction = delta.mul(adjustment * diff);
  if (!p1.isFixed && !p1.anchored) p1.position = p1.position.sum(correction.mul(0.5f));
  if (!p2.isFixed && !p2.anchored) p2.position = p2.position.sub(correction.mul(0.5f));
}
void XPBD_Bending_Advanced(float complianceInput, float betaf, float slopf, float dampingCoefficientf, float yieldStrainf, float plasticRatef, float maxPlasticStrainf, float hardeningf, float subDtf) {
    if (subDtf <= 1e-6f) return;

    final float EPS = 1e-7f;

    // --- 1) Geometria (Igual antes) ---
    Vector3 p1Pos = p1.position; Vector3 p2Pos = p2.position;
    Vector3 p3Pos = p3.position; Vector3 p4Pos = p4.position;

    Vector3 e = p2Pos.sub(p1Pos);
    float elen = e.length();
    if (elen < EPS) return;
    Vector3 eDir = e.mul(1f / elen);

    Vector3 n1 = e.cross(p3Pos.sub(p1Pos));
    Vector3 n2 = e.cross(p4Pos.sub(p1Pos));
    float n1Len = n1.length();
    float n2Len = n2.length();
    if (n1Len < EPS || n2Len < EPS) return;

    Vector3 n1n = n1.mul(1f / n1Len);
    Vector3 n2n = n2.mul(1f / n2Len);

    float dot = n1n.dot(n2n);
    dot = Math.max(-1f, Math.min(1f, dot));
    float currentAngle = (float)Math.acos(dot);

    // --- 2) Constraint e SLOP (Novidade 1) ---
    // Slop: Se o erro for muito pequeno, nem calcula. Economiza CPU e evita tremedeira.
    float C = currentAngle - restAngle;
    if (Math.abs(C) < slopf) return; 

    // --- 3) Plasticidade com Hardening e MaxStrain (Novidade 2 e 3) ---
    // Hardening: Aumenta o limite de elasticidade (yield) baseado no quanto já deformou.
    // Assumindo que 'restAngle' começou perto de 0, o abs(restAngle) é o quanto já deformou.
    float currentHardening = hardeningf * Math.abs(restAngle); 
    float effectiveYield = yieldStrainf + currentHardening;

    if (plasticRatef > 0f && Math.abs(C) > effectiveYield) {
        float sign = (C >= 0f) ? 1f : -1f;
        // Taxa de deformação plástica
        float deltaPlastic = sign * plasticRatef * (Math.abs(C) - effectiveYield) * subDtf;
        
        restAngle += deltaPlastic;

        // Max Plastic Strain: Limita o quanto o metal pode entortar no total
        // (Aqui assumo limites hardcoded de PI, mas o maxPlasticStrainf deve ser menor, ex: 45 graus)
        float limit = maxPlasticStrainf > 0 ? maxPlasticStrainf : (float)Math.PI;
        restAngle = Math.max(-limit, Math.min(limit, restAngle));
        
        // Recalcula C com o novo restAngle para a força elástica atuar apenas no que sobrou
        C = currentAngle - restAngle;
    }
    
    // Check de saída antecipada se a plasticidade resolveu tudo
    if (Math.abs(C) < 1e-9f) return;

    // --- 4) Gradientes Corrigidos pela Geometria ---
    float invH1 = elen / n1Len;
    float invH2 = elen / n2Len;
    Vector3 g3 = eDir.cross(n1n).mul(invH1);
    Vector3 g4 = n2n.cross(eDir).mul(invH2);
    Vector3 gSum = g3.sum(g4);
    Vector3 g1 = gSum.mul(-0.5f);
    Vector3 g2 = gSum.mul(-0.5f);

    // --- 5) Pesos ---
    float w1 = (p1.isFixed || p1.anchored) ? 0f : (1f/p1.rb.getMass());
    float w2 = (p2.isFixed || p2.anchored) ? 0f : (1f/p2.rb.getMass());
    float w3 = (p3.isFixed || p3.anchored) ? 0f : (1f/p3.rb.getMass());
    float w4 = (p4.isFixed || p4.anchored) ? 0f : (1f/p4.rb.getMass());

    float sum = w1 * g1.dot(g1) + w2 * g2.dot(g2) + w3 * g3.dot(g3) + w4 * g4.dot(g4);
    if (sum < EPS) return;

    // --- 6) Compliance e Damping Avançado (Novidade 4) ---
    // alpha = compliance / dt^2
    float alpha = complianceInput / (subDtf * subDtf);
    
    // Damping XPBD: Adiciona viscosidade ao denominador.
    // Existem várias fórmulas, mas esta é a "Rayleigh Damping" aproximada pra XPBD.
    // Ela "suja" o denominador fazendo o sistema resistir a mudanças rápidas.
    float dampingTerm = dampingCoefficientf * subDtf; 
    
    // Se 'betaf' for para integração Newmark ou similar, pode ignorar, 
    // mas se for um fator extra de rigidez viscosa, some ele aqui também.
    // Vou assumir que betaf é um multiplicador extra ou bias.
    
    float denom = sum + alpha + dampingTerm; 
    
    // --- 7) Lambda e Correção ---
    float deltaLambda = -C / denom;

    Vector3 dx1 = g1.mul(w1 * deltaLambda);
    Vector3 dx2 = g2.mul(w2 * deltaLambda);
    Vector3 dx3 = g3.mul(w3 * deltaLambda);
    Vector3 dx4 = g4.mul(w4 * deltaLambda);

    if (w1 > 0f) p1.rb.addVelocity(dx1.mul(1f / subDtf));
    if (w2 > 0f) p2.rb.addVelocity(dx2.mul(1f / subDtf));
    if (w3 > 0f) p3.rb.addVelocity(dx3.mul(1f / subDtf));
    if (w4 > 0f) p4.rb.addVelocity(dx4.mul(1f / subDtf));
}
  
void XPBD_Metal(float ComplianceInput, float Beta, float slop, float dampingCoefficient,float InternalFriction, float yieldStrain, 
float plasticRate, float maxPlasticStrain, float hardening, float subDt) {  
  float dt = subDt;  
  if (dt <= 1e-6f) return;  
  
  // ----------------------------  
  // 1) Compliance / estabilidade  
  // ----------------------------  
  float slider = ComplianceInput;  
  // evita pow(10, -inf) com slider muito pequeno  
  final float alphaMin = 1e-9f;  
  float compliance = (slider <= 0.001f) ? 0f : (float) Math.pow(10f, slider - 10f);  
  if (type == Type.SHEAR) compliance *= 2.0f;  
  if (type == Type.BENDING) compliance *= 0.5f;  
  
  // XPBD usual: alpha = compliance / (dt*dt)  
  float alpha = (compliance <= 0f) ? alphaMin : Math.max(alphaMin, compliance / (dt * dt));  
float gamma = Math.min(alpha / dt, 1f);// termo de estabilização (usado na atualização de lambda)  
  
  // ----------------------------  
  // 2) geometric quantities  
  // ----------------------------  
  float currentLen = currentLength;  
  if (currentLen <= 1e-8f) return;  
  Vector3 dir = delta.mul(1f / currentLen);  
  
  // ----------------------------  
  // 3) plasticidade (metal)  
  // ----------------------------  
  // Variáveis de instância esperadas: float plasticStrain; // estado acumulado  
  //                              float originalRestLength; // comprimento inicial de referência  
  //                              float hardeningAccum; // se quiser acumular algo extra  
  if (plasticRate > 0f) {  
    // strain atual em relação ao restLength atual  
    float curRest = restLength; // restLength agora representa comprimento elástico atual  
    float strain = (currentLen - curRest) / curRest; // positivo = tração, negativo = compressão  
    float absStrain = Math.abs(strain);  
  
    // yield efetivo com hardening dependente da plasticStrain  
    float yieldEff = yieldStrain + hardening * plasticStrain;  
  
    // torna bending mais resistente  
    if (type == Type.BENDING) yieldEff *= 3.0f;  
  
    // compressão pode aumentar resistência  
    if (currentLen < curRest) yieldEff *= 1.5f;  
  
    // se passou do yield, incrementa plasticStrain de forma rate-limited (viscoplastic)  
    if (absStrain > yieldEff) {  
      float excess = absStrain - yieldEff;  
      // usa uma rampa para não aplicar tudo de uma vez  
      float deltaPlastic = sign(strain) * plasticRate * excess * dt;  
      // limita o incremento para não explodir  
      float newPlastic = plasticStrain + deltaPlastic;  
      newPlastic = Math.max(-maxPlasticStrain, Math.min(maxPlasticStrain, newPlastic));  
      float applied = newPlastic - plasticStrain;  
      plasticStrain = newPlastic;  
  
      // atualiza restLength suavemente a partir do original  
      restLength = originalRestLength * (1f + plasticStrain);  
  
      // acumula dureza (opcional)  
      hardeningAccum += Math.abs(applied) * hardening;  
    } else {  
      // quando abaixo do yield, não zera alpha — apenas reduz comportamento plástico  
      // mantém alpha pequeno (já garantido acima)  
    }  
  }  
  
  // ----------------------------  
  // 4) limitar deformações extremas (hard-limit)  
  // ----------------------------  
  float maxStretch = originalRestLength * 0.02f; // 2% sobre original  
  float maxCompress = -originalRestLength * 0.05f; // -5% sobre original  
  float error = currentLen - restLength;  
  error = Math.max(maxCompress, Math.min(error, maxStretch));  
  float clampedLen = restLength + error;
if (clampedLen <= 1e-8f) return;
  // recomputa C a partir do erro limitado  
  // (vamos usar essa C mais adiante)  
  // obs: mantemos restLength como possivel valor plastificado  
  // nota: mantivemos currentLen acima para calcular dir  
  // substituímos C abaixo  
  
  // ----------------------------  
  // 5) massas / weights (usar inverse mass)  
  // ----------------------------  
  final float massScale = 0.1f;  
  float invM1 = (p1.isFixed || p1.anchored) ? 0f : (1f / Math.max(1e-8f, p1.rb.getMass()));  
  float invM2 = (p2.isFixed || p2.anchored) ? 0f : (1f / Math.max(1e-8f, p2.rb.getMass()));  
  float w1 = invM1 * massScale;  
  float w2 = invM2 * massScale;  
  w1 *= 0.05f;  
  w2 *= 0.05f;  
  
  float wTotal = w1 + w2;  
  if (wTotal <= 0f) return;  
  
  
  
  
  
  // ----------------------------  
  // 6) constraint & bias  
  // ----------------------------  
  float C = error;  
  
  C *= (C > 0f) ? 2.5f : 1.5f;  
  
  // slop: deixa uma faixa pequena sem corrigir  
  if (Math.abs(C) <= slop) return;  
  
  float beta = Math.min(Beta, 0.1f);  
  float vRel = p2.rb.getVelocity().sub(p1.rb.getVelocity()).dot(dir);  
  float internalFriction = InternalFriction;  
  
  // bias term clássico XPBD (positional damping converted to velocity-space here)  
  float biasTerm = (beta / dt) * C * internalFriction;  
  
  // ----------------------------  
  // 7) lambda solve (estabilizado)  
  // ----------------------------  
  // evita divisão por zero com gamma garantido acima  
  float numerator = -(vRel + biasTerm + gamma * lambdaAccum);  
  float denominator = wTotal + gamma;  
  float deltaLambda = numerator / denominator;  
  
  // limita lambda acumulado para evitar drift extremo  
  float lambdaLimit = 10f / Math.max(1e-6f, wTotal);
  lambdaAccum = Math.max(-lambdaLimit, Math.min(lambdaLimit, lambdaAccum + deltaLambda));  
  
  Vector3 impulse = dir.mul(deltaLambda);  
  if (w1 > 0f) p1.rb.addVelocity(impulse.mul(-w1));  
  if (w2 > 0f) p2.rb.addVelocity(impulse.mul(w2));  
  
  // ----------------------------  
  // 8) damping direcional (viscous)  
  // ----------------------------  
  Vector3 newVRelVec = p2.rb.getVelocity().sub(p1.rb.getVelocity());  
  float newVRel = newVRelVec.dot(dir);  
  float finalDamping = (internalFriction > 1.0f) ? 25.0f : dampingCoefficient;  
  if (finalDamping > 0.001f) {  
    float dampImpulse = -newVRel * (1.0f - (float) Math.exp(-finalDamping * dt));  
    Vector3 dImp = dir.mul(dampImpulse / wTotal);  
    if (w1 > 0f) p1.rb.addVelocity(dImp.mul(-w1));  
    if (w2 > 0f) p2.rb.addVelocity(dImp.mul(w2));  
  }  
}  




void XPBD_Cloth(float ComplianceInput, float Beta, float slop, float dampingCoefficient, float InternalFriction,float subDt) {
    float dt = subDt; 
    if (dt <= 1e-6f) return;

    // 1) Compliance (Slider 0-1)
    // Para tecidos que não esticam, queremos compliance quase 0 (10^-9)
    float compliance = (ComplianceInput <= 0.001f) ? 0f : (float) Math.pow(10f, ComplianceInput - 10f);
    
    // Shear e Structural precisam de "mordida", Bending pode ser mais mole
    if (type == Type.SHEAR) compliance *= 0.1f; // Força extra nas diagonais
    
    // alpha no XPBD de velocidade é compliance / dt^2
    float alpha = compliance / (dt * dt);

    // 2) Geometria
    Vector3 diff = p2.position.sub(p1.position);
    float currentLen = diff.length();
    if (currentLen <= 1e-8f) return;
    Vector3 dir = diff.mul(1f / currentLen);

    // 3) Massas Inversas
    float w1 = (p1.isFixed || p1.anchored) ? 0f : (1f / Math.max(1e-8f, p1.rb.getMass()));
    float w2 = (p2.isFixed || p2.anchored) ? 0f : (1f / Math.max(1e-8f, p2.rb.getMass()));
    float wTotal = w1 + w2;
    if (wTotal <= 0f) return;

    // 4) O SEGREDO: Bias de Velocidade (Sua força de colisão)
    float C = currentLen - restLength;
    if (Math.abs(C) <= slop) C = 0;
    
    // Beta entre 0.1 e 0.5 para estabilidade, ou 1.0 para rigidez absoluta
    float bias = (Beta / dt) * C;

    // 5) Velocidade Relativa
    float vRel = p2.rb.getVelocity().sub(p1.rb.getVelocity()).dot(dir);

    // 6) Solver XPBD Estabilizado
    // Adicionamos (alpha * dt) no denominador para evitar a tremedeira em pontos fixos
    float numerator = -(vRel + bias + alpha * lambdaAccum * dt);
    float denominator = wTotal + alpha * dt;
    
    float deltaLambda = numerator / (denominator + 1e-9f);
    lambdaAccum += deltaLambda;

    // 7) Aplicação de Impulso (Sem mexer na position na mão!)
    // Deixamos o RB mover a posição através da velocidade, evitando teleporte.
    Vector3 impulse = dir.mul(deltaLambda);
    if (w1 > 0f) p1.rb.addVelocity(impulse.mul(-w1));
    if (w2 > 0f) p2.rb.addVelocity(impulse.mul(w2));



   float friction = InternalFriction;
if (friction > 1e-6f) {

    float dv = -vRel * Math.min(1.0f, friction * dt);

    float impulseMag = dv / (wTotal + 1e-9f);
    Vector3 fImpulse = dir.mul(impulseMag);

    if (w1 > 0f) p1.rb.addVelocity(fImpulse.mul(-w1));
    if (w2 > 0f) p2.rb.addVelocity(fImpulse.mul(w2));
}

    // 8) Damping Direcional (Opcional - limpa vibrações de alta frequência)
    if (dampingCoefficient > 0.001f) {
        float vRelPost = p2.rb.getVelocity().sub(p1.rb.getVelocity()).dot(dir);
        float dImp = -vRelPost * Math.min(1.0f, dampingCoefficient * dt);
        Vector3 dampVec = dir.mul(dImp / wTotal);
        if (w1 > 0f) p1.rb.addVelocity(dampVec.mul(-w1));
        if (w2 > 0f) p2.rb.addVelocity(dampVec.mul(w2));
    }
    
 }





public void satisfy(float complianceInput, float betaf, float slopf, float dampingCoefficientf,float Internalfrictionf, float yieldStrainf, float plasticRatef,
float maxPlasticStrainf, float hardeningf, float Dt_fake,float Dt_real,boolean useTimeStep) { hasRb = (p1.rb != null && p2.rb != null);

if (type == Type.BENDING_ADVANCED) {
if (hasRb) {
XPBD_Bending_Advanced(complianceInput, betaf,slopf, dampingCoefficientf,yieldStrainf,
plasticRatef,maxPlasticStrainf,hardeningf,(useTimeStep)?Dt_fake:Dt_real);
}
return;
}
// --- ABAIXO MANTÉM SUA LÓGICA ORIGINAL DE 2 PONTOS ---
delta = p2.position.sub(p1.position);
currentLength = delta.length();
if (currentLength < 1e-6f) return;

if (!hasRb) PBD();
else {
if (yieldStrainf > 0.0001f) XPBD_Metal(complianceInput, betaf, slopf, dampingCoefficientf,Internalfrictionf, yieldStrainf, plasticRatef, maxPlasticStrainf, hardeningf,(useTimeStep)?Dt_fake:Dt_real);
else XPBD_Cloth(complianceInput, betaf, slopf, dampingCoefficientf,Internalfrictionf,(useTimeStep)?Dt_fake:Dt_real);
}

}