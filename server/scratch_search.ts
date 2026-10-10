import { MongoClient } from "mongodb";

async function run() {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    console.log("no MONGODB_URI");
    return;
  }
  const client = new MongoClient(uri);
  try {
    await client.connect();
    const db = client.db("test_breadcrumb_" + Date.now());
    const coll = db.collection("memories");
    
    console.log("Listing search indexes...");
    const indexes = await coll.listSearchIndexes().toArray().catch(e => {
        console.error("Failed to list search indexes:", e.message);
        return [];
    });
    console.log(JSON.stringify(indexes, null, 2));

  } finally {
    await client.close();
  }
}
run();
