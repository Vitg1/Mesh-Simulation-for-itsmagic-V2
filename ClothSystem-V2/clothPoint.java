/* @Author Sr Mil Games */
@Hide public Vector3 position;
@Hide public Vector3 localPosition;
@Hide public Vector3 previousPosition;

public boolean isFixed = false;

@Hide boolean anchored;

public int vertexIndex;

public SpatialObject anchor;

@Hide Vector3 accumulatedForce = new Vector3();

@AutoWired @Hide Rigidbody rb;

private boolean justReleased;
private boolean initialized;

public void applyForce(Vector3 force) {
  accumulatedForce = accumulatedForce.sum(force);
}

// No clothPoint.simulate:
public void simulate(float timeStep, float damping, float airdrag) {
  if (myObject != null && myObject.exists() && rb == null) myObject.setGlobalPosition(position);

  if (rb == null) {
    if (anchor != null && anchor.exists()) {
      anchored = true;
      // isFixed=true;
      position = anchor.getGlobalPosition();
      previousPosition = anchor.getGlobalPosition();
      return;
    } else anchored = false;

    if (isFixed) return;

    Vector3 velocity = position.sub(previousPosition).mul(damping);

    // aplica air drag
      if(airdrag>0)
    velocity = velocity.mul(1 - airdrag * timeStep);
    
    Vector3 acceleration = accumulatedForce.mul(timeStep * timeStep);
    Vector3 next = position.sum(velocity).sum(acceleration);
    previousPosition = position;
    position = next;
    accumulatedForce.set(0, 0, 0);
    return;
  }

  if (myObject.exists() == true && rb != null) {

    position = myObject.getGlobalPosition();

    // inicialização
    if (!initialized) {
      previousPosition = position;
      initialized = true;
      return;
    }

    if (anchor != null && anchor.exists()) {
      anchored = true;
      position = myObject.getGlobalPosition();
      previousPosition = myObject.getGlobalPosition();
      rb.setEnabled(false);
      myObject.getTransform().setIgnoreParent(false);
      myObject.setGlobalPosition(anchor.getGlobalPosition());
      justReleased = true;

      return;
    } else {
      anchored = false;
      myObject.getTransform().setIgnoreParent(true);
    }

    if (isFixed) {
      rb.setEnabled(false);

      position = myObject.getGlobalPosition();
      previousPosition = position;

      justReleased = true;
      return;
    }
    if (justReleased) {
      // não integra, não aplica solver, não calcula velocidade
      position = myObject.getGlobalPosition();
      previousPosition = position;

      rb.setVelocity(0, 0, 0);
      accumulatedForce.set(0, 0, 0);

      rb.setEnabled(true);

      justReleased = false;
      return;
    }

    // 1. APLICAÇÃO DAS FORÇAS EXTERNAS (Vento, Gravidade Dinâmica, etc)
    // F = m * a   =>   a = F / m
    // deltaV = a * dt

    float mass = rb.getMass();
    if (mass > 0.0001f) {
      // Calcula aceleração baseada na força acumulada
      Vector3 acceleration = accumulatedForce.mul(1f / mass);

      Vector3 deltaVelocity = acceleration.mul(timeStep);
      rb.addVelocity(deltaVelocity);

      if (airdrag > 0) {
        // aplica air drag
        Vector3 v = rb.getVelocity();
        v = v.mul(1 - airdrag * timeStep);
        rb.setVelocity(v);
      } 
    
      // === ATUALIZA ESTADO ===
      previousPosition = position;
      accumulatedForce.set(0, 0, 0);
    }
  }
}