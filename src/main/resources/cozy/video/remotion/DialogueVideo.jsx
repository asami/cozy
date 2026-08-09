import React from "react";
import { AbsoluteFill, Img, interpolate, spring, useCurrentFrame, useVideoConfig } from "remotion";
import { computeDiagramLayout } from "./DiagramLayout.js";

const WIDTH = 1280;
const HEIGHT = 720;

const COLORS = {
  ink: "#181b1f",
  muted: "#4f5963",
  paper: "#fffaf0",
  white: "#ffffff",
  teal: "#00a6b5",
  blue: "#2451ff",
  coral: "#e54f6d",
  gold: "#e3ad28",
  green: "#2fb36f",
  black: "#111317",
};

function sceneInfo(frame, scenes) {
  const index = scenes.findIndex((scene) => frame >= scene.startFrame && frame < scene.startFrame + scene.durationFrames);
  const safeIndex = index >= 0 ? index : scenes.length - 1;
  return {
    scene: scenes[safeIndex],
    index: safeIndex,
  };
}

function sectionFor(scene, index) {
  return scene?.section || scene?.effects?.section || scene?.visual?.section || `scene-${index}`;
}

function sectionMetaFor(sections, sectionId) {
  return (sections || []).find((section) => section.id === sectionId) || {};
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}

function captionGlyphWidth(char, fontSize) {
  if (char === " " || char === "\t") {
    return fontSize * 0.36;
  }
  if (/[\u0020-\u007e]/.test(char)) {
    return fontSize * 0.58;
  }
  if (/[\uff61-\uff9f]/.test(char)) {
    return fontSize * 0.56;
  }
  return fontSize;
}

function estimateCaptionLineCount(text, fontSize, maxWidth) {
  const chars = Array.from(text || "");
  let lines = 1;
  let currentWidth = 0;
  for (const char of chars) {
    if (char === "\n") {
      lines += 1;
      currentWidth = 0;
      continue;
    }
    const width = captionGlyphWidth(char, fontSize);
    if (currentWidth > 0 && currentWidth + width > maxWidth) {
      lines += 1;
      currentWidth = width;
    } else {
      currentWidth += width;
    }
  }
  return lines;
}

function captionLayoutFor(text) {
  const maxWidth = 1064;
  const fontSizes = [31, 29, 27, 25];
  const layouts = fontSizes.map((fontSize) => {
    const lineHeight = Math.round(fontSize * 1.16);
    return {
      fontSize,
      lineHeight,
      lineCount: estimateCaptionLineCount(text, fontSize, maxWidth),
    };
  });
  return layouts.find((layout) => layout.lineCount <= 3) || layouts[layouts.length - 1];
}

function isShowcaseProfile(effectProfile) {
  return effectProfile === "showcase";
}

function isSlideAnimationProfile(effectProfile) {
  return effectProfile === "compact" || isShowcaseProfile(effectProfile);
}

function effectIntensity(effect) {
  if (effect?.intensity === "high") {
    return 1;
  }
  if (effect?.intensity === "low") {
    return 0.45;
  }
  return 0.72;
}

function timingFor(effect) {
  return {
    enterFrames: Number(effect?.timing?.enterFrames || 16),
    exitFrames: Number(effect?.timing?.exitFrames || 10),
    staggerFrames: Number(effect?.timing?.staggerFrames || 4),
  };
}

function titleEffectFor(effect, effectProfile) {
  if (effectProfile === "simple") {
    return effect;
  }
  const timing = timingFor(effect);
  return {
    ...effect,
    timing: {
      ...effect?.timing,
      enterFrames: Math.max(timing.enterFrames, effectProfile === "showcase" ? 34 : 32),
      staggerFrames: Math.max(timing.staggerFrames, effectProfile === "showcase" ? 7 : 6),
    },
  };
}

function springValue(localFrame, fps, effect, offset = 0) {
  const timing = timingFor(effect);
  return spring({
    frame: Math.max(0, localFrame - offset),
    fps,
    durationInFrames: timing.enterFrames,
    config: {
      damping: effect?.textMotion?.includes("bounce") || effect?.textMotion?.includes("pop") ? 8 : 13,
      stiffness: 150,
      mass: 0.78,
    },
  });
}

function exitValue(localFrame, durationFrames, effect) {
  const timing = timingFor(effect);
  return interpolate(
    localFrame,
    [Math.max(0, durationFrames - timing.exitFrames), durationFrames],
    [0, 1],
    { extrapolateLeft: "clamp", extrapolateRight: "clamp" }
  );
}

function entranceStyle(effect, localFrame, durationFrames, fps, effectProfile, index = 0) {
  if (!isSlideAnimationProfile(effectProfile)) {
    return { opacity: 1 };
  }
  const compact = effectProfile === "compact";
  const timing = timingFor(effect);
  const raw = springValue(localFrame, fps, effect, index * timing.staggerFrames);
  const progress = clamp(raw, 0, 1.15);
  const exit = exitValue(localFrame, durationFrames, effect);
  const motion = effect?.textMotion || "stagger-up";
  const opacity = clamp(progress, 0, 1) * (compact ? 1 : 1 - exit * 0.65);
  const distance = interpolate(clamp(progress, 0, 1), [0, 1], [compact ? 18 : 48, 0], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const scale = interpolate(progress, [0, 1], [compact ? 0.98 : 0.86, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "extend",
  });
  const rotate = !compact && (motion.includes("pop") || motion.includes("bounce"))
    ? interpolate(progress, [0, 1], [-4, 0], { extrapolateLeft: "clamp", extrapolateRight: "clamp" })
    : 0;

  if (motion.includes("terminal")) {
    return {
      opacity,
      clipPath: `inset(0 ${interpolate(clamp(progress, 0, 1), [0, 1], [100, 0], {
        extrapolateLeft: "clamp",
        extrapolateRight: "clamp",
      })}% 0 0)`,
      transform: `translateX(${-22 + 22 * clamp(progress, 0, 1)}px)`,
    };
  }

  if (motion.includes("left") || motion.includes("layer")) {
    return {
      opacity,
      transform: `translateX(${interpolate(clamp(progress, 0, 1), [0, 1], [-64, 0], {
        extrapolateLeft: "clamp",
        extrapolateRight: "clamp",
      }) * (compact ? 0.35 : 1)}px) scale(${scale})`,
    };
  }

  if (motion.includes("converge")) {
    const direction = index % 2 === 0 ? -1 : 1;
    return {
      opacity,
      transform: `translateX(${direction * distance}px) scale(${scale})`,
    };
  }

  return {
    opacity,
    transform: `translateY(${distance}px) scale(${scale}) rotate(${rotate}deg)`,
  };
}

function cameraStyle(effect, localFrame, durationFrames, fps, effectProfile) {
  if (!isShowcaseProfile(effectProfile)) {
    return "none";
  }
  const progress = clamp(springValue(localFrame, fps, effect), 0, 1);
  const exit = exitValue(localFrame, durationFrames, effect);
  const camera = effect?.camera || "push-in";
  const cycle = Math.sin((localFrame / Math.max(1, durationFrames)) * Math.PI);
  const shake = effect?.accent?.includes("flash") && localFrame < 10
    ? Math.sin(localFrame * 1.2) * 3.5 * effectIntensity(effect)
    : 0;

  if (camera === "fast-push") {
    return `translate(${shake}px, ${shake * 0.25}px) scale(${0.93 + progress * 0.13 - exit * 0.05})`;
  }
  if (camera === "slow-orbit") {
    return `translate(${Math.sin(localFrame / 24) * 12}px, ${Math.cos(localFrame / 30) * 7}px) rotate(${Math.sin(localFrame / 38) * 0.65}deg) scale(${1.015 + cycle * 0.025})`;
  }
  if (camera === "track-right") {
    return `translateX(${-24 + progress * 24 + cycle * 16}px)`;
  }
  if (camera === "micro-zoom") {
    return `scale(${1 + progress * 0.035 + cycle * 0.01})`;
  }
  return `translate(${shake}px, 0) scale(${1 + progress * 0.045 + cycle * 0.018 - exit * 0.02})`;
}

function paletteFor(effect, sceneIndex) {
  const preset = effect?.preset || "";
  if (preset === "concept") {
    return [COLORS.teal, COLORS.coral, COLORS.gold];
  }
  if (preset === "tech" || preset === "implementation") {
    return [COLORS.blue, COLORS.teal, COLORS.green];
  }
  if (preset === "closing" || preset === "title") {
    return [COLORS.coral, COLORS.gold, COLORS.teal];
  }
  const variants = [
    [COLORS.teal, COLORS.blue, COLORS.gold],
    [COLORS.coral, COLORS.teal, COLORS.gold],
    [COLORS.green, COLORS.blue, COLORS.coral],
  ];
  return variants[sceneIndex % variants.length];
}

function MotionBackdrop({ effect, frame, localFrame, sceneIndex, effectProfile }) {
  const [a, b, c] = paletteFor(effect, sceneIndex);
  const intensity = effectIntensity(effect);
  if (!isShowcaseProfile(effectProfile)) {
    return (
      <>
        <div
          style={{
            ...styles.backdropBase,
            background: `linear-gradient(135deg, ${a}18 0%, transparent 36%, ${b}14 64%, ${c}18 100%)`,
            opacity: 0.86,
          }}
        />
        <div
          style={{
            ...styles.grid,
            opacity: 0.22 + intensity * 0.04,
          }}
        />
        <div
          style={{
            ...styles.ribbon,
            borderColor: `${c}66`,
            transform: "rotate(8deg)",
          }}
        />
      </>
    );
  }
  const enter = interpolate(localFrame, [0, 24], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const sweep = (frame * (0.55 + intensity * 0.35) + sceneIndex * 73) % 1600;
  const gridShift = frame * 0.55;
  const nodeOpacity = 0.2 + intensity * 0.18;

  return (
    <>
      <div
        style={{
          ...styles.backdropBase,
          background: `linear-gradient(135deg, ${a}22 0%, transparent 32%, ${b}1c 62%, ${c}20 100%)`,
          opacity: 0.9,
        }}
      />
      <div
        style={{
          ...styles.grid,
          backgroundPosition: `${-gridShift}px ${gridShift * 0.45}px`,
          opacity: 0.28 + intensity * 0.08,
        }}
      />
      <div
        style={{
          ...styles.diagonalBand,
          background: `linear-gradient(90deg, transparent, ${a}66, ${b}55, transparent)`,
          transform: `translateX(${sweep - 560}px) rotate(-18deg)`,
          opacity: 0.32 + intensity * 0.16,
        }}
      />
      <div
        style={{
          ...styles.ribbon,
          borderColor: `${c}88`,
          transform: `translate(${Math.sin(frame / 28) * 18}px, ${Math.cos(frame / 34) * 12}px) rotate(${8 + Math.sin(frame / 50) * 3}deg) scale(${0.9 + enter * 0.18})`,
        }}
      />
      {[0, 1, 2, 3, 4, 5].map((item) => (
        <div
          key={item}
          style={{
            ...styles.node,
            left: 120 + item * 190 + Math.sin((frame + item * 17) / 30) * 16,
            top: 96 + ((item * 83) % 450) + Math.cos((frame + item * 23) / 36) * 12,
            background: [a, b, c][item % 3],
            opacity: nodeOpacity,
            transform: `scale(${0.85 + Math.sin((frame + item * 8) / 18) * 0.18})`,
          }}
        />
      ))}
      <div
        style={{
          ...styles.cornerCode,
          color: a,
          opacity: 0.16 + intensity * 0.08,
          transform: `translateY(${Math.sin(frame / 22) * 4}px)`,
        }}
      >
        {effect?.preset || "remotion"} / {String(sceneIndex + 1).padStart(2, "0")}
      </div>
    </>
  );
}

function TransitionMatte({ effect, localFrame, durationFrames, sceneIndex, effectProfile, sectionStart, sectionTitle }) {
  const [a, b] = paletteFor(effect, sceneIndex);
  if (effectProfile === "compact") {
    if (!sectionStart) {
      return null;
    }
    const intro = interpolate(localFrame, [0, 28], [0, 1], {
      extrapolateLeft: "clamp",
      extrapolateRight: "clamp",
    });
    const bandX = interpolate(intro, [0, 1], [-520, 1400], {
      extrapolateLeft: "clamp",
      extrapolateRight: "clamp",
    });
    const reveal = interpolate(intro, [0, 0.38, 0.72, 1], [0, 1, 1, 0], {
      extrapolateLeft: "clamp",
      extrapolateRight: "clamp",
    });
    return (
      <>
        <div
          style={{
            ...styles.compactFade,
            background: `linear-gradient(90deg, ${a}3d, ${b}30, transparent 72%)`,
            opacity: reveal * 0.8,
          }}
        />
        <div
          style={{
            ...styles.compactWipeWide,
            background: `linear-gradient(90deg, ${a}, ${b}, ${a})`,
            transform: `translateX(${bandX}px) skewX(-18deg)`,
            opacity: reveal,
          }}
        />
        <div
          style={{
            ...styles.compactWipeCore,
            background: COLORS.white,
            transform: `translateX(${bandX - 62}px) skewX(-18deg)`,
            opacity: reveal * 0.92,
          }}
        />
        <div
          style={{
            ...styles.sectionBadge,
            borderColor: `${a}88`,
            color: a,
            opacity: reveal,
            transform: `translateX(${interpolate(intro, [0, 1], [-34, 0], {
              extrapolateLeft: "clamp",
              extrapolateRight: "clamp",
            })}px)`,
          }}
        >
          <span style={{ ...styles.sectionBadgeMark, background: `linear-gradient(180deg, ${a}, ${b})` }} />
          {sectionTitle || `Section ${sceneIndex + 1}`}
        </div>
      </>
    );
  }
  const intro = interpolate(localFrame, [0, 16], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  const outro = exitValue(localFrame, durationFrames, effect);
  return (
    <>
      <div
        style={{
          ...styles.wipe,
          background: `linear-gradient(90deg, ${a}, ${b})`,
          transform: `translateX(${interpolate(intro, [0, 1], [0, 1380], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          })}px) skewX(-18deg)`,
          opacity: 1 - intro,
        }}
      />
      <div
        style={{
          ...styles.outroFlash,
          background: `linear-gradient(90deg, transparent, ${a}88, ${b}66, transparent)`,
          opacity: outro,
          transform: `translateX(${interpolate(outro, [0, 1], [-500, 900], {
            extrapolateLeft: "clamp",
            extrapolateRight: "clamp",
          })}px) skewX(-18deg)`,
        }}
      />
    </>
  );
}

function TitleVisual({ visual, effect, localFrame, durationFrames, fps, sceneIndex, effectProfile }) {
  const [a, b, c] = paletteFor(effect, sceneIndex);
  const titleEffect = titleEffectFor(effect, effectProfile);
  const titleSlashOpacity = interpolate(localFrame, [8, 28], [0, 1], {
    extrapolateLeft: "clamp",
    extrapolateRight: "clamp",
  });
  return (
    <div style={{ ...styles.titleStage, transform: cameraStyle(effect, localFrame, durationFrames, fps, effectProfile) }}>
      {effectProfile !== "simple" ? (
        <div
          style={{
            ...styles.titleSlash,
            background: `linear-gradient(180deg, ${a}, ${b})`,
            opacity: titleSlashOpacity,
          }}
        />
      ) : null}
      <p style={{ ...styles.eyebrow, color: a, ...entranceStyle(titleEffect, localFrame, durationFrames, fps, effectProfile, 0) }}>
        {visual.eyebrow}
      </p>
      <h1 style={{ ...styles.titleHeading, ...entranceStyle(titleEffect, localFrame, durationFrames, fps, effectProfile, 1) }}>
        {visual.heading}
      </h1>
      <p style={{ ...styles.titleSubtitle, ...entranceStyle(titleEffect, localFrame, durationFrames, fps, effectProfile, 2) }}>
        {visual.subtitle || visual.detail}
      </p>
      <div style={styles.titleItems}>
        {(visual.items || []).map((item, index) => (
          <span
            key={item}
            style={{
              ...styles.titleChip,
              borderColor: [a, b, c][index % 3],
              color: [a, b, c][index % 3],
              ...entranceStyle(titleEffect, localFrame, durationFrames, fps, effectProfile, index + 3),
            }}
          >
            {item}
          </span>
        ))}
      </div>
      <p style={{ ...styles.footer, ...entranceStyle(titleEffect, localFrame, durationFrames, fps, effectProfile, 7) }}>
        {visual.footer}
      </p>
    </div>
  );
}

function ImageVisual({ visual, effect, localFrame, durationFrames, fps, sceneIndex, effectProfile }) {
  const [a, b, c] = paletteFor(effect, sceneIndex);
  const hasNotes = visual.items?.length > 0 || visual.detail;
  const largeDiagram = visual.layout !== "compact";
  return (
    <div style={{ ...styles.imageStage, ...(largeDiagram ? styles.imageStageLarge : null), transform: cameraStyle(effect, localFrame, durationFrames, fps, effectProfile) }}>
      <div style={{ ...styles.slideAccent, background: `linear-gradient(180deg, ${a}, ${b}, ${c})` }} />
      <div style={{ ...styles.slideLabel, color: a, borderColor: `${a}66` }}>{effect?.preset || "diagram"}</div>
      <h1 style={{ ...styles.imageHeading, ...entranceStyle(effect, localFrame, durationFrames, fps, effectProfile, 0) }}>
        {visual.heading}
      </h1>
      <div style={{ ...styles.diagramFrame, ...(largeDiagram ? (hasNotes ? styles.diagramFrameLargeWithNotes : styles.diagramFrameLarge) : null), ...entranceStyle(effect, localFrame, durationFrames, fps, effectProfile, 1) }}>
        <Img src={visual.image} alt={visual.imageAlt || visual.heading || ""} style={styles.diagramImage} />
      </div>
      {hasNotes ? (
        <div style={{ ...styles.imageNotes, ...entranceStyle(effect, localFrame, durationFrames, fps, effectProfile, 2) }}>
          {visual.items ? visual.items.map((item) => (
            <span key={item} style={{ ...styles.imageNote, borderColor: `${a}66` }}>{item}</span>
          )) : visual.detail}
        </div>
      ) : null}
    </div>
  );
}

function DiagramVisual({ scene, effect, localFrame, durationFrames, fps, sceneIndex, effectProfile }) {
  const visual = scene.visual;
  const [a, b, c] = paletteFor(effect, sceneIndex);
  const largeDiagram = visual.layout !== "compact";
  const layout = computeDiagramLayout(visual.diagram, {
    sceneId: scene.id,
    canvas: largeDiagram ? { width: 1004, height: 382 } : { width: 780, height: 316 },
  });
  return (
    <div style={{ ...styles.imageStage, ...(largeDiagram ? styles.imageStageLarge : null), transform: cameraStyle(effect, localFrame, durationFrames, fps, effectProfile) }}>
      <div style={{ ...styles.slideAccent, background: `linear-gradient(180deg, ${a}, ${b}, ${c})` }} />
      <div style={{ ...styles.slideLabel, color: a, borderColor: `${a}66` }}>{effect?.preset || "diagram"}</div>
      {visual.heading ? (
        <h1 style={{ ...styles.imageHeading, ...entranceStyle(effect, localFrame, durationFrames, fps, effectProfile, 0) }}>
          {visual.heading}
        </h1>
      ) : null}
      <div style={{ ...styles.declarativeDiagramFrame, ...(largeDiagram ? styles.declarativeDiagramFrameLarge : null), ...entranceStyle(effect, localFrame, durationFrames, fps, effectProfile, 1) }}>
        <svg width={layout.canvas.width} height={layout.canvas.height} viewBox={`0 0 ${layout.canvas.width} ${layout.canvas.height}`} style={styles.declarativeDiagramEdges} aria-hidden="true">
          <defs>
            <marker id={`diagram-arrow-${scene.id}`} markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
              <path d="M0,0 L8,4 L0,8 z" fill={a} />
            </marker>
          </defs>
          {layout.edges.map((edge) => (
            <polyline
              key={`${edge.from}-${edge.to}`}
              points={edge.points.map((point) => `${point.x},${point.y}`).join(" ")}
              fill="none"
              stroke={a}
              strokeWidth="3"
              strokeLinejoin="round"
              markerEnd={`url(#diagram-arrow-${scene.id})`}
            />
          ))}
        </svg>
        {layout.nodes.map((node, index) => (
          <div
            key={node.id}
            style={{
              ...styles.declarativeDiagramNode,
              left: node.x,
              top: node.y,
              width: node.width,
              height: node.height,
              fontSize: node.fontSize,
              lineHeight: `${Math.round(node.fontSize * 1.2)}px`,
              borderColor: `${[a, b, c][index % 3]}aa`,
            }}
          >
            {node.lines.map((line, lineIndex) => <span key={`${node.id}-${lineIndex}`}>{line}</span>)}
          </div>
        ))}
      </div>
    </div>
  );
}

function ArchitectureLayersVisual({ visual, effect, localFrame, durationFrames, fps, sceneIndex, effectProfile }) {
  const [a, b, c] = paletteFor(effect, sceneIndex);
  const layers = visual.layers || [];
  const focusLayer = visual.focusLayer || "";
  const stableLayout = Boolean(visual.stableLayout || focusLayer);
  const highlightMotion = visual.highlightMotion || "none";
  const archEntrance = (index = 0) => (
    stableLayout
      ? { opacity: 1, transform: "none" }
      : entranceStyle(effect, localFrame, durationFrames, fps, effectProfile, index)
  );
  const highlightHop = (isFocus) => {
    if (!isFocus || highlightMotion !== "hop" || !isSlideAnimationProfile(effectProfile)) {
      return "";
    }
    const hopProgress = clamp(spring({
      frame: localFrame,
      fps,
      durationInFrames: 18,
      config: { damping: 6.5, stiffness: 190, mass: 0.62 },
    }), 0, 1.18);
    const lift = Math.sin(clamp(hopProgress, 0, 1) * Math.PI) * -8;
    const scale = 1.018 + Math.max(0, hopProgress - 1) * 0.018;
    return ` translateY(${lift}px) scale(${scale})`;
  };
  return (
    <div style={{ ...styles.archStage, transform: cameraStyle(effect, localFrame, durationFrames, fps, effectProfile) }}>
      <div style={{ ...styles.slideAccent, background: `linear-gradient(180deg, ${a}, ${b}, ${c})` }} />
      <div style={{ ...styles.slideLabel, color: a, borderColor: `${a}66` }}>{effect?.preset || "architecture"}</div>
      <h1 style={{ ...styles.archHeading, ...archEntrance(0) }}>
        {visual.heading}
      </h1>
      {visual.detail ? (
        <p style={{ ...styles.archDetail, ...archEntrance(1) }}>
          {visual.detail}
        </p>
      ) : null}
      <div style={styles.archLayers}>
        {layers.map((layer, index) => {
          const color = [a, b, c, a][index % 4];
          const isFocus = focusLayer && focusLayer === layer.id;
          const layerEntrance = archEntrance(index + 2);
          const focusTransform = `${isFocus ? "scale(1.018)" : "scale(1)"}${highlightHop(isFocus)}`;
          return (
            <div
              key={layer.id || layer.label}
              style={{
                ...styles.archLayer,
                ...layerEntrance,
                borderColor: isFocus ? `${color}cc` : "rgba(24,27,31,.12)",
                background: isFocus
                  ? `linear-gradient(90deg, ${color}28, rgba(255,255,255,.94))`
                  : "rgba(255,255,255,.86)",
                transform: stableLayout ? focusTransform : layerEntrance.transform,
              }}
            >
              <div style={{ ...styles.archLayerMark, background: `linear-gradient(180deg, ${color}, ${[b, c, a][index % 3]})` }} />
              <div style={styles.archLayerMain}>
                <div style={styles.archLayerLabel}>{layer.label}</div>
                <div style={styles.archLayerDetail}>{layer.detail}</div>
              </div>
              {layer.tags?.length ? (
                <div style={styles.archTags}>
                  {layer.tags.map((tag) => (
                    <span key={tag} style={{ ...styles.archTag, borderColor: `${color}66`, color }}>{tag}</span>
                  ))}
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function LateNoteOverlay({ lateNote, effect, localFrame, durationFrames, fps, sceneIndex }) {
  const [a, b, c] = paletteFor(effect, sceneIndex);
  const lateStart = Math.round(durationFrames * Number(lateNote?.startRatio || 0.52));
  const lateProgress = lateNote
    ? interpolate(localFrame, [lateStart, lateStart + Math.round(fps * 0.8)], [0, 1], {
      extrapolateLeft: "clamp",
      extrapolateRight: "clamp",
    })
    : 0;
  if (!lateNote) {
    return null;
  }
  return (
    <div
      style={{
        ...styles.lateNote,
        borderColor: `${a}88`,
        opacity: lateProgress,
        transform: `translateY(${interpolate(lateProgress, [0, 1], [118, 0], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
        })}px)`,
      }}
    >
      <span style={{ ...styles.lateNoteMark, background: `linear-gradient(180deg, ${a}, ${b})` }} />
      <div>
        <div style={{ ...styles.lateNoteLabel, color: a }}>{lateNote.label || "NOTE"}</div>
        <div>{lateNote.text}</div>
      </div>
    </div>
  );
}

function itemHasUrl(item) {
  if (typeof item === "string") {
    return /https?:\/\//.test(item);
  }
  return Boolean(item?.url);
}

function itemKey(item, index) {
  if (typeof item === "string") {
    return item;
  }
  return `${item?.title || item?.label || "item"}-${item?.url || index}`;
}

function ItemContent({ item, urlHeavy }) {
  if (typeof item === "string") {
    return <span style={urlHeavy ? styles.itemTextUrl : styles.itemText}>{item}</span>;
  }
  const title = item.title || item.label || "";
  return (
    <span style={styles.itemNested}>
      <span style={styles.itemNestedTitle}>{title}</span>
      {item.subtitle ? <span style={styles.itemNestedSubtitle}>{item.subtitle}</span> : null}
      {item.url ? <span style={styles.itemNestedUrl}>{item.url}</span> : null}
    </span>
  );
}

function SlideVisual({ visual, effect, localFrame, durationFrames, fps, sceneIndex, effectProfile }) {
  const [a, b, c] = paletteFor(effect, sceneIndex);
  const itemCount = visual.items?.length || 0;
  const urlHeavy = (visual.items || []).some((item) => itemHasUrl(item));
  const compact = itemCount >= 5 || urlHeavy;
  const headingText = visual.heading || "";
  const headingFontSize = compact || headingText.length > 15 ? 39 : 45;
  const lateNote = visual.lateNote;
  return (
    <div style={{ ...styles.slideStage, ...(compact ? styles.slideStageCompact : null), transform: cameraStyle(effect, localFrame, durationFrames, fps, effectProfile) }}>
      <div style={{ ...styles.slideAccent, background: `linear-gradient(180deg, ${a}, ${b}, ${c})` }} />
      <div style={{ ...styles.slideLabel, color: a, borderColor: `${a}66` }}>{effect?.preset || "scene"}</div>
      <h1 style={{ ...styles.slideHeading, fontSize: headingFontSize, marginBottom: compact ? 15 : 23, ...entranceStyle(effect, localFrame, durationFrames, fps, effectProfile, 0) }}>
        {visual.heading}
      </h1>
      {visual.subtitle ? (
        <div style={{ ...styles.slideSubtitle, borderColor: `${a}66`, color: a, ...entranceStyle(effect, localFrame, durationFrames, fps, effectProfile, 1) }}>
          {visual.subtitle}
        </div>
      ) : null}
      {visual.items ? (
        <div style={{ ...styles.itemStack, gap: compact ? 7 : 10 }}>
          {visual.items.map((item, index) => (
            <div
              key={itemKey(item, index)}
              style={{
                ...styles.itemCard,
                ...(compact ? styles.itemCardCompact : null),
                ...(urlHeavy ? styles.itemCardUrl : null),
                borderLeftColor: [a, b, c][index % 3],
                ...entranceStyle(effect, localFrame, durationFrames, fps, effectProfile, index + (visual.subtitle ? 2 : 1)),
              }}
            >
              <span style={{ ...styles.itemNumber, color: [a, b, c][index % 3] }}>
                {String(index + 1).padStart(2, "0")}
              </span>
              <ItemContent item={item} urlHeavy={urlHeavy} />
            </div>
          ))}
        </div>
      ) : (
        <p style={{ ...styles.detail, ...entranceStyle(effect, localFrame, durationFrames, fps, effectProfile, 1) }}>
          {visual.detail}
        </p>
      )}
      <LateNoteOverlay
        lateNote={lateNote}
        effect={effect}
        localFrame={localFrame}
        durationFrames={durationFrames}
        fps={fps}
        sceneIndex={sceneIndex}
      />
    </div>
  );
}

function Visual({ scene, effect, localFrame, fps, sceneIndex, effectProfile, scenes }) {
  if (!scene.visual) {
    return null;
  }
  if (scene.visual.baseSceneId) {
    const baseIndex = scenes.findIndex((candidate) => candidate.id === scene.visual.baseSceneId);
    const baseScene = baseIndex >= 0 ? scenes[baseIndex] : null;
    const baseFrame = baseScene ? Math.max(0, baseScene.durationFrames - 1) : localFrame;
    return (
      <>
        {baseScene ? (
          <Visual
            scene={baseScene}
            effect={baseScene.effects || effect}
            localFrame={baseFrame}
            fps={fps}
            sceneIndex={baseIndex}
            effectProfile={effectProfile}
            scenes={scenes}
          />
        ) : null}
        <div style={styles.lateNoteStage}>
          <LateNoteOverlay
            lateNote={scene.visual.lateNote}
            effect={effect}
            localFrame={localFrame}
            durationFrames={scene.durationFrames}
            fps={fps}
            sceneIndex={sceneIndex}
          />
        </div>
      </>
    );
  }
  if (scene.visual.kind === "title-card") {
    return (
      <TitleVisual
        visual={scene.visual}
        effect={effect}
        localFrame={localFrame}
        durationFrames={scene.durationFrames}
        fps={fps}
        sceneIndex={sceneIndex}
        effectProfile={effectProfile}
      />
    );
  }
  if (scene.visual.kind === "architecture-layers") {
    return (
      <ArchitectureLayersVisual
        visual={scene.visual}
        effect={effect}
        localFrame={localFrame}
        durationFrames={scene.durationFrames}
        fps={fps}
        sceneIndex={sceneIndex}
        effectProfile={effectProfile}
      />
    );
  }
  if (scene.visual.kind === "diagram") {
    return (
      <DiagramVisual
        scene={scene}
        effect={effect}
        localFrame={localFrame}
        durationFrames={scene.durationFrames}
        fps={fps}
        sceneIndex={sceneIndex}
        effectProfile={effectProfile}
      />
    );
  }
  if (scene.visual.image) {
    return (
      <ImageVisual
        visual={scene.visual}
        effect={effect}
        localFrame={localFrame}
        durationFrames={scene.durationFrames}
        fps={fps}
        sceneIndex={sceneIndex}
        effectProfile={effectProfile}
      />
    );
  }
  return (
    <SlideVisual
      visual={scene.visual}
      effect={effect}
      localFrame={localFrame}
      durationFrames={scene.durationFrames}
      fps={fps}
      sceneIndex={sceneIndex}
      effectProfile={effectProfile}
      scenes={scenes}
    />
  );
}

function Character({ character, active, localFrame, sceneIndex, fps, effectProfile, diagramMode = false }) {
  if (!character) {
    return null;
  }
  const mouthOpen = active && localFrame > 4 && Math.floor(localFrame / 4) % 2 === 0;
  const src = mouthOpen ? character.mouthOpenAsset : character.mouthClosedAsset;
  const side = character.side || "left";
  const flipX = Boolean(character.flipX);
  const enter = isShowcaseProfile(effectProfile)
    ? clamp(spring({
      frame: localFrame - (side === "left" ? 8 : 12),
      fps,
      durationInFrames: 18,
      config: { damping: 8, stiffness: 120, mass: 0.8 },
    }), 0, 1.12)
    : 1;
  const bob = 0;
  const scale = isShowcaseProfile(effectProfile) ? (active ? 1.08 : 0.92) : 1;
  const x = side === "left" ? -54 + enter * 54 : 54 - enter * 54;
  const shadow = character.shadow || {};
  const shadowX = Number(shadow.x ?? 12);
  const shadowY = Number(shadow.y ?? 18);
  const shadowBlur = Number(shadow.blur ?? 0);
  const shadowColor = shadow.color || "rgba(0,0,0,.20)";
  const characterWidth = diagramMode ? 190 : 252;
  return (
    <div
      style={{
        ...styles.characterWrap,
        ...(diagramMode ? styles.characterWrapDiagram : null),
        left: side === "left" ? (diagramMode ? 18 : 26) : "auto",
        right: side === "right" ? (diagramMode ? 18 : 26) : "auto",
        width: characterWidth,
        opacity: isShowcaseProfile(effectProfile) ? (active ? 1 : 0.48) : 1,
        transform: `translate(${x}px, ${bob}px) scale(${scale})`,
      }}
    >
      <div style={{ ...styles.speakerRing, opacity: active ? 0.78 : 0 }} />
      <div
        style={{
          filter: `drop-shadow(${shadowX}px ${shadowY}px ${shadowBlur}px ${shadowColor})`,
          position: "relative",
          width: characterWidth,
          zIndex: 2,
        }}
      >
        <Img
          src={src || character.asset}
          style={{
            ...styles.character,
            width: characterWidth,
            filter: "none",
            transform: flipX ? "scaleX(-1)" : "none",
          }}
        />
      </div>
    </div>
  );
}

function Caption({ character, line, localFrame, durationFrames, effect, sceneIndex, fps, effectProfile }) {
  if (!character || !line) {
    return null;
  }
  const [a] = paletteFor(effect, sceneIndex);
  const text = line;
  const captionLayout = captionLayoutFor(text);
  const { fontSize, lineHeight, lineCount } = captionLayout;
  const availableTextHeight = 108;
  const scrollDistance = Math.max(0, lineCount * lineHeight - availableTextHeight + 8);
  const shouldScroll = lineCount > 3;
  const scrollStart = Math.round(fps * 0.75);
  const scrollEnd = Math.max(scrollStart + 1, durationFrames - Math.round(fps * 0.75));
  const scrollY = shouldScroll
    ? -interpolate(localFrame, [scrollStart, scrollEnd], [0, scrollDistance], {
      extrapolateLeft: "clamp",
      extrapolateRight: "clamp",
    })
    : 0;
  const enter = isShowcaseProfile(effectProfile)
    ? clamp(spring({
      frame: localFrame - 5,
      fps,
      durationInFrames: 12,
      config: { damping: 10, stiffness: 160, mass: 0.7 },
    }), 0, 1)
    : 1;
  return (
    <div
      style={{
        ...styles.caption,
        fontSize,
        alignItems: shouldScroll ? "flex-start" : "center",
        transform: isShowcaseProfile(effectProfile) ? `translateY(${interpolate(enter, [0, 1], [34, 0], {
          extrapolateLeft: "clamp",
          extrapolateRight: "clamp",
        })}px)` : "none",
        opacity: enter,
      }}
    >
      <div style={{ ...styles.captionStripe, background: a }} />
      <div
        style={{
          ...styles.captionText,
          lineHeight: `${lineHeight}px`,
          transform: `translateY(${scrollY}px)`,
        }}
      >
        {text}
      </div>
    </div>
  );
}

function Progress({ frame, fps, scenes, sceneIndex, effect }) {
  const totalFrames = scenes[scenes.length - 1].startFrame + scenes[scenes.length - 1].durationFrames;
  const width = `${clamp(frame / Math.max(1, totalFrames), 0, 1) * 100}%`;
  const [a, b] = paletteFor(effect, sceneIndex);
  return (
    <div style={styles.progressShell}>
      <div style={{ ...styles.progressFill, width, background: `linear-gradient(90deg, ${a}, ${b})` }} />
      <div style={styles.timeCode}>
        {String(sceneIndex + 1).padStart(2, "0")} / {String(scenes.length).padStart(2, "0")} &nbsp; {(frame / fps).toFixed(1)}s
      </div>
    </div>
  );
}

export function DialogueVideo({ characters, sections, scenes, fps, effectProfile = "compact" }) {
  const frame = useCurrentFrame();
  const config = useVideoConfig();
  const effectiveFps = fps || config.fps;
  const showcase = isShowcaseProfile(effectProfile);
  const { scene, index } = sceneInfo(frame, scenes);
  const currentSection = sectionFor(scene, index);
  const previousSection = index > 0 ? sectionFor(scenes[index - 1], index - 1) : null;
  const sectionStart = index > 0 && currentSection !== previousSection;
  const sectionTitle = sectionMetaFor(sections, currentSection).title || currentSection;
  const localFrame = Math.max(0, frame - scene.startFrame);
  const leadInFrames = Number(scene.leadInFrames || 0);
  const speechLocalFrame = Math.max(0, localFrame - leadInFrames);
  const speechStarted = localFrame >= leadInFrames;
  const speechDurationFrames = scene.audioDuration ? Math.max(1, Math.round(Number(scene.audioDuration) * effectiveFps)) : scene.durationFrames;
  const speechActive = speechStarted && speechLocalFrame < speechDurationFrames;
  const effect = scene.effects || {};
  const activeCharacter = scene.speaker ? characters[scene.speaker] : null;
  const diagramMode = Boolean(scene.visual?.image || scene.visual?.kind === "diagram") && scene.visual?.layout !== "compact";

  return (
    <AbsoluteFill style={styles.root}>
      <MotionBackdrop effect={effect} frame={frame} localFrame={localFrame} sceneIndex={index} effectProfile={effectProfile} />
      <Visual scene={scene} effect={effect} localFrame={localFrame} fps={effectiveFps} sceneIndex={index} effectProfile={effectProfile} scenes={scenes} />
      {scene.speaker && !scene.silent && Object.entries(characters).map(([key, character]) => (
        <Character
          key={key}
          character={character}
          active={speechActive && key === scene.speaker}
          localFrame={speechLocalFrame}
          sceneIndex={index}
          fps={effectiveFps}
          effectProfile={effectProfile}
          diagramMode={diagramMode}
        />
      ))}
      {scene.speaker && !scene.silent && speechStarted ? (
        <Caption
          character={activeCharacter}
          line={scene.caption || scene.line}
          localFrame={speechLocalFrame}
          durationFrames={speechDurationFrames}
          effect={effect}
          sceneIndex={index}
          fps={effectiveFps}
          effectProfile={effectProfile}
        />
      ) : null}
      {showcase ? <Progress frame={frame} fps={effectiveFps} scenes={scenes} sceneIndex={index} effect={effect} /> : null}
      {effectProfile !== "simple" ? (
        <TransitionMatte
          effect={effect}
          localFrame={localFrame}
          durationFrames={scene.durationFrames}
          sceneIndex={index}
          effectProfile={effectProfile}
          sectionStart={sectionStart}
          sectionTitle={sectionTitle}
        />
      ) : null}
    </AbsoluteFill>
  );
}

const styles = {
  root: {
    width: WIDTH,
    height: HEIGHT,
    background: `linear-gradient(135deg, ${COLORS.paper} 0%, #eef8f6 45%, #fff3e4 100%)`,
    fontFamily: "'Hiragino Sans', 'Noto Sans JP', sans-serif",
    color: COLORS.ink,
    overflow: "hidden",
  },
  backdropBase: {
    position: "absolute",
    inset: 0,
  },
  grid: {
    position: "absolute",
    inset: -80,
    backgroundImage:
      "linear-gradient(90deg, rgba(24,27,31,.095) 1px, transparent 1px), linear-gradient(0deg, rgba(24,27,31,.075) 1px, transparent 1px)",
    backgroundSize: "52px 52px",
  },
  diagonalBand: {
    position: "absolute",
    top: -220,
    bottom: -220,
    width: 320,
    filter: "blur(4px)",
  },
  ribbon: {
    position: "absolute",
    right: -90,
    top: 38,
    width: 520,
    height: 520,
    border: "42px solid",
    borderRadius: 18,
    opacity: 0.18,
  },
  node: {
    position: "absolute",
    width: 14,
    height: 14,
    borderRadius: 14,
    boxShadow: "0 0 22px currentColor",
  },
  cornerCode: {
    position: "absolute",
    right: 38,
    bottom: 158,
    fontSize: 18,
    fontWeight: 800,
    letterSpacing: 0,
    textTransform: "uppercase",
  },
  wipe: {
    position: "absolute",
    top: -120,
    left: -280,
    width: 360,
    height: 960,
  },
  compactWipeWide: {
    position: "absolute",
    top: -180,
    left: 0,
    width: 390,
    height: 1080,
    boxShadow: "0 0 44px rgba(255,255,255,.75)",
    pointerEvents: "none",
  },
  compactWipeCore: {
    position: "absolute",
    top: -180,
    left: 0,
    width: 44,
    height: 1080,
    filter: "blur(1px)",
    pointerEvents: "none",
  },
  compactFade: {
    position: "absolute",
    inset: 0,
    pointerEvents: "none",
  },
  sectionBadge: {
    position: "absolute",
    left: 74,
    top: 72,
    minWidth: 320,
    maxWidth: 760,
    minHeight: 54,
    display: "flex",
    alignItems: "center",
    gap: 14,
    padding: "10px 22px 10px 16px",
    background: "rgba(255,255,255,.94)",
    border: "2px solid",
    borderRadius: 8,
    boxShadow: "0 20px 46px rgba(24,27,31,.20)",
    fontSize: 26,
    lineHeight: 1.18,
    fontWeight: 900,
    letterSpacing: 0,
    textTransform: "uppercase",
    pointerEvents: "none",
  },
  sectionBadgeMark: {
    flex: "0 0 auto",
    width: 12,
    alignSelf: "stretch",
    borderRadius: 4,
  },
  outroFlash: {
    position: "absolute",
    top: -120,
    left: -280,
    width: 360,
    height: 960,
    pointerEvents: "none",
  },
  titleStage: {
    position: "absolute",
    left: 92,
    top: 58,
    width: 1096,
    height: 502,
    padding: "40px 54px 42px 72px",
    boxSizing: "border-box",
  },
  titleSlash: {
    position: "absolute",
    left: 8,
    top: 28,
    width: 18,
    height: 420,
    borderRadius: 6,
    boxShadow: "0 14px 42px rgba(0,0,0,.18)",
  },
  eyebrow: {
    fontSize: 22,
    fontWeight: 900,
    margin: "0 0 20px",
    textTransform: "uppercase",
  },
  titleHeading: {
    width: 970,
    fontSize: 58,
    lineHeight: 1.06,
    margin: "0 0 18px",
    fontWeight: 900,
    letterSpacing: 0,
    textShadow: "0 10px 28px rgba(255,255,255,.95)",
  },
  titleSubtitle: {
    width: 850,
    color: COLORS.muted,
    fontSize: 28,
    lineHeight: 1.32,
    margin: "0 0 24px",
    fontWeight: 700,
  },
  titleItems: {
    display: "flex",
    gap: 10,
    flexWrap: "wrap",
  },
  titleChip: {
    fontSize: 18,
    fontWeight: 900,
    padding: "8px 15px",
    background: "rgba(255,255,255,.72)",
    border: "2px solid",
    borderRadius: 8,
    boxShadow: "0 12px 30px rgba(25,25,25,.08)",
  },
  footer: {
    position: "absolute",
    left: 72,
    bottom: 14,
    color: COLORS.muted,
    fontSize: 18,
    fontWeight: 800,
  },
  slideStage: {
    position: "absolute",
    left: 286,
    top: 46,
    width: 728,
    minHeight: 492,
    padding: "34px 34px 32px 42px",
    boxSizing: "border-box",
    background: "rgba(255,255,255,.82)",
    border: "1px solid rgba(24,27,31,.11)",
    borderRadius: 14,
    boxShadow: "0 34px 86px rgba(24,27,31,.14)",
  },
  slideStageCompact: {
    top: 42,
    minHeight: 500,
    padding: "31px 34px 28px 42px",
  },
  imageStage: {
    position: "absolute",
    left: 202,
    top: 34,
    width: 876,
    height: 506,
    padding: "26px 28px 26px 38px",
    boxSizing: "border-box",
    background: "rgba(255,255,255,.88)",
    border: "1px solid rgba(24,27,31,.11)",
    borderRadius: 14,
    boxShadow: "0 34px 86px rgba(24,27,31,.14)",
  },
  imageStageLarge: {
    left: 78,
    top: 24,
    width: 1124,
    height: 526,
    padding: "24px 28px 24px 38px",
  },
  slideAccent: {
    position: "absolute",
    left: 0,
    top: 0,
    bottom: 0,
    width: 10,
    borderRadius: "14px 0 0 14px",
  },
  slideLabel: {
    display: "inline-block",
    fontSize: 15,
    fontWeight: 900,
    padding: "6px 12px",
    border: "1px solid",
    borderRadius: 6,
    background: "rgba(255,255,255,.68)",
    marginBottom: 15,
    textTransform: "uppercase",
  },
  slideHeading: {
    fontSize: 45,
    lineHeight: 1.16,
    margin: "0 0 23px",
    fontWeight: 900,
    letterSpacing: 0,
  },
  imageHeading: {
    fontSize: 31,
    lineHeight: 1.12,
    margin: "0 0 12px",
    fontWeight: 900,
    letterSpacing: 0,
  },
  diagramFrame: {
    width: "100%",
    height: 340,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    padding: 12,
    boxSizing: "border-box",
    background: "rgba(255,255,255,.92)",
    border: "1px solid rgba(24,27,31,.12)",
    borderRadius: 10,
    overflow: "hidden",
  },
  diagramFrameLarge: {
    height: 398,
    padding: 8,
  },
  diagramFrameLargeWithNotes: {
    height: 350,
    padding: 8,
  },
  declarativeDiagramFrame: {
    position: "relative",
    width: "100%",
    height: 340,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    overflow: "hidden",
    background: "rgba(255,255,255,.92)",
    border: "1px solid rgba(24,27,31,.12)",
    borderRadius: 10,
  },
  declarativeDiagramFrameLarge: {
    height: 398,
  },
  declarativeDiagramEdges: {
    position: "absolute",
    inset: 0,
    overflow: "visible",
  },
  declarativeDiagramNode: {
    position: "absolute",
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    justifyContent: "center",
    boxSizing: "border-box",
    padding: "10px 12px",
    background: "rgba(255,255,255,.98)",
    border: "2px solid",
    borderRadius: 9,
    boxShadow: "0 10px 25px rgba(24,27,31,.13)",
    color: COLORS.ink,
    fontSize: 21,
    lineHeight: "25px",
    fontWeight: 900,
    textAlign: "center",
    overflow: "hidden",
  },
  diagramImage: {
    maxWidth: "100%",
    maxHeight: "100%",
    objectFit: "contain",
  },
  imageNotes: {
    display: "flex",
    gap: 8,
    flexWrap: "wrap",
    marginTop: 10,
  },
  imageNote: {
    display: "inline-flex",
    alignItems: "center",
    minHeight: 34,
    padding: "6px 10px",
    background: "rgba(255,255,255,.82)",
    border: "1px solid",
    borderRadius: 7,
    fontSize: 18,
    lineHeight: 1.12,
    fontWeight: 900,
  },
  archStage: {
    position: "absolute",
    left: 190,
    top: 30,
    width: 900,
    height: 514,
    padding: "24px 30px 26px 40px",
    boxSizing: "border-box",
    background: "rgba(255,255,255,.9)",
    border: "1px solid rgba(24,27,31,.11)",
    borderRadius: 14,
    boxShadow: "0 34px 86px rgba(24,27,31,.14)",
  },
  archHeading: {
    fontSize: 32,
    lineHeight: 1.12,
    margin: "0 0 8px",
    fontWeight: 900,
    letterSpacing: 0,
  },
  archDetail: {
    margin: "0 0 10px",
    color: COLORS.muted,
    fontSize: 18,
    lineHeight: 1.28,
    fontWeight: 800,
  },
  archLayers: {
    display: "grid",
    gap: 8,
  },
  archLayer: {
    position: "relative",
    display: "grid",
    gridTemplateColumns: "12px 1fr 212px",
    gap: 14,
    alignItems: "center",
    minHeight: 78,
    padding: "10px 12px 10px 0",
    border: "2px solid",
    borderRadius: 10,
    boxShadow: "0 14px 32px rgba(24,27,31,.08)",
  },
  archLayerMark: {
    alignSelf: "stretch",
    borderRadius: "8px 0 0 8px",
  },
  archLayerMain: {
    minWidth: 0,
  },
  archLayerLabel: {
    fontSize: 24,
    lineHeight: 1.08,
    fontWeight: 950,
    color: COLORS.ink,
    marginBottom: 5,
  },
  archLayerDetail: {
    fontSize: 17,
    lineHeight: 1.24,
    fontWeight: 800,
    color: COLORS.muted,
  },
  archTags: {
    display: "flex",
    gap: 5,
    flexWrap: "wrap",
    justifyContent: "flex-end",
  },
  archTag: {
    display: "inline-flex",
    alignItems: "center",
    minHeight: 24,
    padding: "3px 7px",
    border: "1px solid",
    borderRadius: 6,
    background: "rgba(255,255,255,.74)",
    fontSize: 13,
    lineHeight: 1.05,
    fontWeight: 900,
  },
  itemStack: {
    display: "flex",
    flexDirection: "column",
    gap: 10,
  },
  itemCard: {
    display: "flex",
    gap: 14,
    alignItems: "flex-start",
    background: "rgba(255,255,255,.84)",
    border: "1px solid rgba(24,27,31,.1)",
    borderLeft: "6px solid",
    borderRadius: 8,
    padding: "12px 15px 12px 13px",
    fontSize: 25,
    lineHeight: 1.28,
    fontWeight: 800,
    boxShadow: "0 14px 28px rgba(24,27,31,.08)",
  },
  itemCardCompact: {
    padding: "9px 13px 9px 12px",
    fontSize: 22,
    lineHeight: 1.22,
  },
  itemCardUrl: {
    padding: "7px 11px 7px 10px",
    fontSize: 17,
    lineHeight: 1.16,
  },
  itemNumber: {
    flex: "0 0 auto",
    fontSize: 17,
    lineHeight: 1.35,
    fontWeight: 900,
  },
  itemText: {
    minWidth: 0,
  },
  itemTextUrl: {
    minWidth: 0,
    overflowWrap: "anywhere",
    wordBreak: "break-word",
  },
  itemNested: {
    minWidth: 0,
    display: "flex",
    flexDirection: "column",
    gap: 4,
  },
  itemNestedTitle: {
    fontSize: 18,
    lineHeight: 1.18,
    fontWeight: 900,
  },
  itemNestedSubtitle: {
    fontSize: 15,
    lineHeight: 1.18,
    fontWeight: 800,
    color: COLORS.muted,
  },
  itemNestedUrl: {
    fontSize: 13,
    lineHeight: 1.18,
    fontWeight: 800,
    color: COLORS.muted,
    overflowWrap: "anywhere",
    wordBreak: "break-word",
  },
  lateNoteStage: {
    position: "absolute",
    left: 286,
    top: 46,
    width: 728,
    minHeight: 492,
    pointerEvents: "none",
  },
  lateNote: {
    position: "absolute",
    left: 42,
    right: 32,
    bottom: 24,
    minHeight: 96,
    display: "flex",
    alignItems: "center",
    gap: 14,
    padding: "14px 18px 14px 16px",
    boxSizing: "border-box",
    background: "rgba(255,255,255,.96)",
    border: "2px solid",
    borderRadius: 8,
    boxShadow: "0 24px 56px rgba(24,27,31,.24)",
    fontSize: 24,
    lineHeight: 1.26,
    fontWeight: 900,
    pointerEvents: "none",
  },
  lateNoteMark: {
    flex: "0 0 auto",
    width: 12,
    alignSelf: "stretch",
    borderRadius: 4,
  },
  lateNoteLabel: {
    fontSize: 15,
    lineHeight: 1,
    marginBottom: 8,
    fontWeight: 900,
    letterSpacing: 0,
    textTransform: "uppercase",
  },
  detail: {
    fontSize: 30,
    lineHeight: 1.45,
    margin: 0,
    fontWeight: 800,
  },
  slideSubtitle: {
    alignSelf: "flex-start",
    margin: "-8px 0 16px",
    padding: "7px 13px",
    border: "2px solid",
    borderRadius: 6,
    background: "rgba(255,255,255,.82)",
    fontSize: 22,
    lineHeight: 1.22,
    fontWeight: 900,
  },
  characterWrap: {
    position: "absolute",
    width: 252,
    bottom: 162,
    transition: "none",
  },
  characterWrapDiagram: {
    bottom: 158,
  },
  character: {
    position: "relative",
    width: 252,
    filter: "drop-shadow(12px 18px 0 rgba(0,0,0,.20))",
    zIndex: 2,
  },
  speakerRing: {
    position: "absolute",
    left: 12,
    right: 12,
    bottom: 4,
    height: 118,
    borderRadius: "50%",
    background: "radial-gradient(ellipse at center, rgba(255,255,255,.92), rgba(0,166,181,.24), transparent 70%)",
    filter: "blur(2px)",
    zIndex: 1,
  },
  caption: {
    position: "absolute",
    left: 72,
    bottom: 18,
    width: 1136,
    minHeight: 126,
    maxHeight: 144,
    overflow: "hidden",
    background: "rgba(16,18,22,.94)",
    borderRadius: 10,
    color: COLORS.white,
    lineHeight: 1.34,
    padding: "18px 30px 18px 42px",
    boxSizing: "border-box",
    display: "flex",
    alignItems: "center",
    fontWeight: 800,
    boxShadow: "0 22px 48px rgba(0,0,0,.26)",
  },
  captionText: {
    display: "block",
    transition: "none",
    overflowWrap: "anywhere",
  },
  captionStripe: {
    position: "absolute",
    left: 0,
    top: 0,
    bottom: 0,
    width: 10,
    borderRadius: "10px 0 0 10px",
  },
  progressShell: {
    position: "absolute",
    left: 44,
    top: 22,
    right: 44,
    height: 24,
    borderRadius: 7,
    background: "rgba(255,255,255,.56)",
    border: "1px solid rgba(24,27,31,.1)",
    overflow: "hidden",
  },
  progressFill: {
    height: "100%",
  },
  timeCode: {
    position: "absolute",
    right: 10,
    top: 3,
    fontSize: 13,
    fontWeight: 900,
    color: COLORS.black,
  },
};
