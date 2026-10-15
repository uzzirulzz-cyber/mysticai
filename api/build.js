module.exports = async (req, res) => {
  // Redirect API calls to the real Cloudflare Pages backend
  const target = "https://mysticai-d27.pages.dev/api/tasks/execute";
  res.status(307).setHeader("Location", target).json({
    redirect: target,
    message: "API has moved to Cloudflare Pages"
  });
};
