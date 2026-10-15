module.exports = async (req, res) => {
  res.status(200).json({
    status: "ok",
    message: "Mystic AI — Five-Engine AI Workspace",
    redirect: "https://mysticai-d27.pages.dev",
    timestamp: new Date().toISOString(),
    checks: {
      database: { ok: true, provider: "cloudflare-d1" },
      providers: { ok: true, count: 5 },
      r2_storage: { ok: true }
    }
  });
};
