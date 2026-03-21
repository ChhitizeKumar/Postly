// ============================================================
// NexusHub MongoDB Initialization
// ============================================================

db = db.getSiblingDB('nexushub_content');

// Create collections with validators
db.createCollection('posts', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['title', 'authorId', 'status'],
      properties: {
        title: { bsonType: 'string', minLength: 1, maxLength: 300 },
        slug: { bsonType: 'string' },
        content: { bsonType: 'string' },
        excerpt: { bsonType: 'string', maxLength: 500 },
        authorId: { bsonType: 'string' },
        authorUsername: { bsonType: 'string' },
        status: { enum: ['DRAFT', 'PUBLISHED', 'ARCHIVED'] },
        tags: { bsonType: 'array' },
        coverImageUrl: { bsonType: 'string' },
        likesCount: { bsonType: 'int' },
        commentsCount: { bsonType: 'int' },
        viewsCount: { bsonType: 'int' },
        readingTimeMinutes: { bsonType: 'int' },
        aiGenerated: { bsonType: 'bool' },
        createdAt: { bsonType: 'date' },
        updatedAt: { bsonType: 'date' },
        publishedAt: { bsonType: 'date' }
      }
    }
  }
});

db.createCollection('comments');
db.createCollection('likes');
db.createCollection('tags');

// Indexes for posts
db.posts.createIndex({ slug: 1 }, { unique: true, sparse: true });
db.posts.createIndex({ authorId: 1, status: 1 });
db.posts.createIndex({ status: 1, publishedAt: -1 });
db.posts.createIndex({ tags: 1 });
db.posts.createIndex({ title: 'text', content: 'text', excerpt: 'text' });

// Indexes for comments
db.comments.createIndex({ postId: 1, createdAt: -1 });
db.comments.createIndex({ authorId: 1 });

// Indexes for likes
db.likes.createIndex({ postId: 1, userId: 1 }, { unique: true });
db.likes.createIndex({ userId: 1 });

// Indexes for tags
db.tags.createIndex({ name: 1 }, { unique: true });
db.tags.createIndex({ postsCount: -1 });

// Seed initial tags
db.tags.insertMany([
  { name: 'javascript', displayName: 'JavaScript', postsCount: 0, color: '#F7DF1E' },
  { name: 'java', displayName: 'Java', postsCount: 0, color: '#ED8B00' },
  { name: 'spring-boot', displayName: 'Spring Boot', postsCount: 0, color: '#6DB33F' },
  { name: 'react', displayName: 'React', postsCount: 0, color: '#61DAFB' },
  { name: 'microservices', displayName: 'Microservices', postsCount: 0, color: '#FF6B6B' },
  { name: 'ai', displayName: 'AI & ML', postsCount: 0, color: '#8B5CF6' },
  { name: 'kafka', displayName: 'Apache Kafka', postsCount: 0, color: '#231F20' },
  { name: 'docker', displayName: 'Docker', postsCount: 0, color: '#2496ED' },
  { name: 'kubernetes', displayName: 'Kubernetes', postsCount: 0, color: '#326CE5' },
  { name: 'devops', displayName: 'DevOps', postsCount: 0, color: '#FF9900' }
]);

print('MongoDB initialized successfully!');
