// ================================================================
// Postly MongoDB — Content Service Setup
// ================================================================

db = db.getSiblingDB('postly_content');

// --- Posts collection ---
db.createCollection('posts');
db.posts.createIndex({ slug: 1 },                          { unique: true, sparse: true });
db.posts.createIndex({ authorId: 1, status: 1 });
db.posts.createIndex({ status: 1, publishedAt: -1 });
db.posts.createIndex({ tags: 1 });
db.posts.createIndex({ title: 'text', content: 'text' },   { weights: { title: 3, content: 1 } });

// --- Comments collection ---
db.createCollection('comments');
db.comments.createIndex({ postId: 1, createdAt: -1 });
db.comments.createIndex({ authorId: 1 });

// --- Likes collection ---
db.createCollection('likes');
db.likes.createIndex({ postId: 1, userId: 1 }, { unique: true });

// --- Tags collection ---
db.createCollection('tags');
db.tags.createIndex({ name: 1 }, { unique: true });

// Seed some starter tags
db.tags.insertMany([
  { name: 'java',           postsCount: 0, color: '#ED8B00' },
  { name: 'spring-boot',   postsCount: 0, color: '#6DB33F' },
  { name: 'react',         postsCount: 0, color: '#61DAFB' },
  { name: 'microservices', postsCount: 0, color: '#FF6B6B' },
  { name: 'kafka',         postsCount: 0, color: '#231F20' },
  { name: 'docker',        postsCount: 0, color: '#2496ED' },
  { name: 'ai',            postsCount: 0, color: '#8B5CF6' },
]);

print('MongoDB: postly_content ready');
